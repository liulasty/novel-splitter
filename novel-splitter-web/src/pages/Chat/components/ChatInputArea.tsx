import { Send, Loader2 } from "lucide-react";

interface ChatInputAreaProps {
    inputValue: string;
    isPending: boolean;
    disabled: boolean;
    placeholder: string;
    actions: {
        setInputValue: (val: string) => void;
        handleSend: () => void;
        handleKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => void;
    };
}

export function ChatInputArea({ inputValue, isPending, disabled, placeholder, actions }: ChatInputAreaProps) {
    return (
        <div className="px-4 py-3 border-t border-gray-100 bg-gray-50/50 flex gap-2 items-center">
            <input
                type="text"
                placeholder={placeholder}
                value={inputValue}
                onChange={(e) => actions.setInputValue(e.target.value)}
                onKeyDown={actions.handleKeyDown}
                disabled={disabled}
                className="flex-1 h-10 rounded-full border border-gray-200 bg-white px-4 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-400 disabled:opacity-50"
            />
            <button
                onClick={actions.handleSend}
                disabled={disabled || !inputValue.trim()}
                className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-blue-500 hover:from-violet-600 hover:to-blue-600 text-white flex items-center justify-center transition-all disabled:opacity-40 disabled:pointer-events-none shadow-sm"
            >
                {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            </button>
        </div>
    );
}