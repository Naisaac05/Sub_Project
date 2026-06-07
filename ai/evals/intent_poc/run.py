# -*- coding: utf-8 -*-
"""
PoC 전체를 한 명령으로 실행한다: augment.py -> evaluate.py

evaluate 로 넘길 인자는 그대로 전달된다.
  python evals/intent_poc/run.py
  python evals/intent_poc/run.py --classifier phase1
"""
import os
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).parent
ENV = {**os.environ, "PYTHONUTF8": "1"}  # Windows 콘솔 인코딩 회피


def step(script, *extra):
    print(f"[run] {script} {' '.join(extra)}".rstrip(), flush=True)
    subprocess.run([sys.executable, str(HERE / script), *extra], check=True, env=ENV)


if __name__ == "__main__":
    step("augment.py")
    step("evaluate.py", *sys.argv[1:])
