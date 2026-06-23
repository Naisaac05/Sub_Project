from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
AI_ROOT = ROOT / "ai"
BACKEND_ROOT = ROOT / "backend"
FRONTEND_ROOT = ROOT / "frontend"

CARD_VERIFIERS = {
    "spring-valid": "spring_valid",
    "spring-profile": "spring_profile",
    "spring-circuit": "resilience4j",
    "spring-aop": "spring_aop",
    "spring-responseentity": "spring_responseentity",
    "frontend-usecallback": "node",
    "frontend-react-server-components": "node",
    "frontend-useref": "node",
    "frontend-dom": "node",
}

READINESS = {
    "java": "ready",
    "java_g1": "ready",
    "java_checked": "ready",
    "python": "ready",
    "spring_valid": "ready",
    "spring_profile": "ready",
    "spring_aop": "ready",
    "spring_responseentity": "ready",
    "resilience4j": "dependency_missing",
    "node": "ready",
    "react_renderer": "dependency_missing",
    "rsc_runtime": "harness_missing",
}


def _run(command: list[str], cwd: Path, timeout: int = 90) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, capture_output=True, text=True, timeout=timeout, check=False)


def verifier_readiness(card_id: str, validator: str | None = None) -> str:
    return READINESS.get(validator or CARD_VERIFIERS.get(card_id, ""), "harness_missing")


def _result(process: subprocess.CompletedProcess[str], failure: str) -> dict:
    return {
        "passed": process.returncode == 0,
        "status": "verified" if process.returncode == 0 else "failed",
        "reason": None if process.returncode == 0 else failure,
        "stderr": process.stderr[-4000:],
    }


def _verify_java(code: str, *, g1: bool = False, checked: bool = False) -> dict:
    with tempfile.TemporaryDirectory(prefix="concept_java_") as temp:
        root = Path(temp)
        (root / "ConceptCheck.java").write_text(_java_source(code), encoding="utf-8")
        compiled = _run(["javac", "-encoding", "UTF-8", "ConceptCheck.java"], root)
        if compiled.returncode:
            return _result(compiled, "javac_failed")
        if checked:
            invalid = "import java.io.IOException; class InvalidChecked { static void load() throws IOException {} public static void main(String[] a) { load(); } }"
            (root / "InvalidChecked.java").write_text(invalid, encoding="utf-8")
            if _run(["javac", "-encoding", "UTF-8", "InvalidChecked.java"], root).returncode == 0:
                return {"passed": False, "status": "failed", "reason": "checked_exception_negative_compile_unexpectedly_passed"}
        command = ["java", "-ea"]
        if g1:
            command.append("-XX:+UseG1GC")
        command.append("ConceptCheck")
        return _result(_run(command, root), "java_execution_failed")


def _java_source(code: str) -> str:
    if "class ConceptCheck" in code:
        return code
    lines = code.splitlines()
    imports = [line for line in lines if line.strip().startswith("import ")]
    statements = [line for line in lines if not line.strip().startswith("import ")]
    indented = "\n".join(f"        {line}" for line in statements)
    prefix = "\n".join(imports)
    return f"{prefix}\npublic class ConceptCheck {{\n    public static void main(String[] args) {{\n{indented}\n    }}\n}}\n"


def _verify_spring(test_name: str) -> dict:
    command = [str(BACKEND_ROOT / "gradlew.bat"), "test", "--tests", f"com.devmatch.ragverify.ConceptExampleVerificationTest.{test_name}"]
    return _result(_run(command, BACKEND_ROOT, timeout=180), "spring_execution_failed")


def verify(validator: str, code: str, *, card_id: str = "") -> dict:
    validator = CARD_VERIFIERS.get(card_id, validator)
    if validator == "java":
        return _verify_java(code)
    if validator == "java_g1":
        return _verify_java(code, g1=True)
    if validator == "java_checked":
        return _verify_java(code, checked=True)
    if validator == "python":
        with tempfile.TemporaryDirectory(prefix="concept_python_") as temp:
            path = Path(temp) / "check.py"
            path.write_text(code, encoding="utf-8")
            return _result(_run([str(AI_ROOT / ".venv" / "Scripts" / "python.exe"), str(path)], Path(temp)), "python_execution_failed")
    if validator == "node":
        return _result(
            _run(["node", "--input-type=module", "--eval", code], FRONTEND_ROOT),
            "node_execution_failed",
        )
    if validator == "spring_valid":
        return _verify_spring("validatesRequestedGroup")
    if validator == "spring_profile":
        return _verify_spring("selectsBeanForActiveProfile")
    if validator == "spring_aop":
        return _verify_spring("appliesAroundAdvice")
    if validator == "spring_responseentity":
        return _verify_spring("buildsResponseEntity")
    readiness = verifier_readiness(card_id, validator)
    return {"passed": False, "status": "unavailable", "reason": f"{readiness}:{validator or 'unknown'}"}
