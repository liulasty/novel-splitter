import subprocess
import json

files = [
    "README.md",
    "application/USAGE.md",
    "application/src/main/java/com/novel/splitter/application/controller/DownloadController.java",
    "application/src/main/java/com/novel/splitter/application/controller/NovelController.java",
    "application/src/main/java/com/novel/splitter/application/controller/SplitController.java",
    "application/src/main/java/com/novel/splitter/application/runner/SplitCommandRunner.java",
    "application/src/main/java/com/novel/splitter/application/service/SplitService.java",
    "application/src/main/java/com/novel/splitter/application/service/download/DownloadService.java",
    "application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeService.java",
    "application/src/main/java/com/novel/splitter/application/service/novel/NovelFacadeServiceImpl.java",
    "domain/src/main/java/com/novel/splitter/domain/model/dto/DownloadAndIngestRequest.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/impl/SequentialPipeline.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/orchestrator/SplitNovelUseCase.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/stages/LoadStage.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/stages/SaveStage.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/stages/SplitStage.java",
    "pipeline/src/main/java/com/novel/splitter/pipeline/stages/ValidationStage.java"
]

report = {}

for f in files:
    try:
        diff_output = subprocess.check_output(
            ["git", "--no-pager", "diff", "origin/master...trae/solo-agent-5foXLL", "--", f],
            text=True
        )
        report[f] = diff_output
    except Exception as e:
        report[f] = str(e)

with open("diff_output.json", "w") as out:
    json.dump(report, out)
