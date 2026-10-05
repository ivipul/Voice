#!/usr/bin/env python3
"""Turns index.html into the body-only fragment the claude.ai Artifact
publisher expects (it wraps the fragment in its own document skeleton).

Usage: python3 tools/make_artifact.py OUT_FILE [SOURCE_HTML]   (from web/crawl; the source defaults to index.html)
"""
import re
import sys

src = open(sys.argv[2] if len(sys.argv) > 2 else "index.html", encoding="utf-8").read()
body = re.search(r"<body>(.*)</body>", src, re.S).group(1)
head = re.search(r"<head>(.*)</head>", src, re.S).group(1)
keep = "\n".join(
    line for line in head.splitlines()
    if line.strip().startswith(("<title", "<link rel=\"stylesheet\"", "<link rel=\"preconnect\""))
)
out = keep + "\n" + body
with open(sys.argv[1], "w", encoding="utf-8") as f:
    f.write(out)
print("wrote", sys.argv[1], len(out), "bytes")
