#!/usr/bin/env python3
"""Stream the current demo terminal JPEG to an emulator's videofile camera.

Uses WebM/VP9: the emulator's bundled FFmpeg does not decode MJPEG. Run this
before opening the image-auto camera; the output FIFO must already exist.
"""
import argparse
import subprocess
import time

from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    output = parser.parse_args().output.resolve()
    while True:
        encoder = subprocess.Popen([
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-f', 'image2pipe', '-vcodec', 'mjpeg', '-r', '6', '-i', 'pipe:0',
            '-an', '-c:v', 'libvpx-vp9', '-deadline', 'realtime', '-cpu-used', '8',
            '-threads', '2', '-g', '1', '-pix_fmt', 'yuv420p',
            '-f', 'webm', '-live', '1', '-cluster_time_limit', '100',
            '-flush_packets', '1', str(output / 'camera.mjpeg'),
        ], stdin=subprocess.PIPE)
        try:
            while encoder.poll() is None:
                encoder.stdin.write((output / 'feed.jpg').read_bytes())
                encoder.stdin.flush()
                time.sleep(1 / 6)
        except BrokenPipeError:
            pass
        finally:
            try:
                encoder.stdin.close()
            except BrokenPipeError:
                pass
            encoder.wait()
        time.sleep(.25)


if __name__ == '__main__':
    main()
