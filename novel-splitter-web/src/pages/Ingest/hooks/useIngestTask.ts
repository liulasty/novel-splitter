import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { downloadApi } from "@/api/downloadApi";

const SESSION_KEY = 'kb:currentNovelId';

export function useIngestTask() {
    const queryClient = useQueryClient();
    const [searchParams, setSearchParams] = useSearchParams();

    const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [novelName, setNovelName] = useState<string>("");
    const [downloadUrl, setDownloadUrl] = useState("");
    const [version, setVersion] = useState("v1");
    const [maxTokens, setMaxTokens] = useState(512);
    const [overlapTokens, setOverlapTokens] = useState(64);
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);
    const initRef = useRef(false);

    const persistCurrentNovelId = useCallback(
        (novelId: string) => {
            const id = novelId.trim();
            setCurrentNovelId(id);
            if (id) {
                try { sessionStorage.setItem(SESSION_KEY, id); } catch { /* ignore */ }
                setSearchParams(
                    (prev) => {
                        const p = new URLSearchParams(prev);
                        p.set('novelId', id);
                        return p;
                    },
                    { replace: true }
                );
            }
        },
        [setSearchParams]
    );

    const clearCurrentNovelId = useCallback(() => {
        setCurrentNovelId('');
        try { sessionStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
        setSearchParams(
            (prev) => {
                const p = new URLSearchParams(prev);
                p.delete('novelId');
                return p;
            },
            { replace: true }
        );
    }, [setSearchParams]);

    useEffect(() => {
        const fromUrl = searchParams.get('novelId')?.trim();
        if (fromUrl) {
            setCurrentNovelId(fromUrl);
            try { sessionStorage.setItem(SESSION_KEY, fromUrl); } catch { /* ignore */ }
            initRef.current = true;
            return;
        }
        if (!initRef.current) {
            initRef.current = true;
            try {
                const fromSession = sessionStorage.getItem(SESSION_KEY)?.trim();
                if (fromSession) {
                    setCurrentNovelId(fromSession);
                    setSearchParams(
                        (prev) => {
                            const p = new URLSearchParams(prev);
                            p.set('novelId', fromSession);
                            return p;
                        },
                        { replace: true }
                    );
                }
            } catch { /* ignore */ }
        }
    }, [searchParams, setSearchParams]);

    const uploadMutation = useMutation({
        mutationFn: novelApi.uploadNovel,
        onSuccess: (data) => {
            const msg = `上传成功！Novel ID: ${data.novelId}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            persistCurrentNovelId(data.novelId);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const downloadAndIngestMutation = useMutation({
        mutationFn: (params: { url: string; name: string }) =>
            downloadApi.downloadAndIngest({
                url: params.url,
                name: params.name,
                version,
                maxScenes: 0,
                chunkSize: maxTokens,
                chunkOverlap: overlapTokens,
            }),
        onSuccess: (data) => {
            const msg = `下载已登记：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            setDownloadUrl("");
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            const msg = `下载入库失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files?.[0]) {
            setSelectedFile(e.target.files[0]);
            setNovelName(e.target.files[0].name.replace('.txt', ''));
            setIngestStatus("");
            setIsError(false);
        }
    };

    const handleUpload = () => {
        if (selectedFile) {
            uploadMutation.mutate(selectedFile);
        }
    };

    const handleDownloadAndIngest = () => {
        if (!downloadUrl || !novelName) {
            setIngestStatus("请填写下载地址和小说名称");
            setIsError(true);
            return;
        }
        downloadAndIngestMutation.mutate({
            url: downloadUrl,
            name: novelName,
        });
    };

    return {
        state: {
            activeTab,
            selectedFile,
            novelName,
            downloadUrl,
            version,
            maxTokens,
            overlapTokens,
            currentNovelId,
            ingestStatus,
            isError,
            isUploading: uploadMutation.isPending,
            isDownloading: downloadAndIngestMutation.isPending,
        },
        actions: {
            setActiveTab,
            setNovelName,
            setDownloadUrl,
            setVersion,
            setMaxTokens,
            setOverlapTokens,
            handleFileChange,
            handleUpload,
            handleDownloadAndIngest,
            clearSelectedNovel: clearCurrentNovelId,
        },
    };
}
