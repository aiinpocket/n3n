#!/usr/bin/env python3
"""
UI/UX Design System Verifier

Verifies screenshots against the design system standards using visual analysis.
"""

import json
import os
from pathlib import Path
from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple
from PIL import Image
import colorsys

# Design System Standards
DESIGN_SYSTEM = {
    "colors": {
        "dark_theme": {
            "bg_primary": "#020617",
            "bg_secondary": "#0F172A",
            "bg_elevated": "#1E293B",
            "primary": "#6366F1",
            "success": "#22C55E",
            "warning": "#F59E0B",
            "danger": "#EF4444",
            "text_primary": "#F8FAFC",
            "text_secondary": "#94A3B8",
        },
        "light_theme": {
            "bg_primary": "#FFFFFF",
            "bg_secondary": "#F8FAFC",
            "text_primary": "#0F172A",
        },
    },
    "contrast_requirements": {
        "normal_text": 4.5,  # WCAG AA
        "large_text": 3.0,
        "ui_components": 3.0,
    },
}


@dataclass
class VerificationResult:
    passed: bool
    score: float  # 0-100
    checks: List[Dict]
    recommendations: List[str]


def hex_to_rgb(hex_color: str) -> Tuple[int, int, int]:
    """Convert hex color to RGB tuple"""
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))


def rgb_to_hex(r: int, g: int, b: int) -> str:
    """Convert RGB to hex color"""
    return f"#{r:02x}{g:02x}{b:02x}"


def get_luminance(r: int, g: int, b: int) -> float:
    """Calculate relative luminance for WCAG contrast"""
    def adjust(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * adjust(r) + 0.7152 * adjust(g) + 0.0722 * adjust(b)


def get_contrast_ratio(color1: str, color2: str) -> float:
    """Calculate WCAG contrast ratio between two colors"""
    rgb1 = hex_to_rgb(color1)
    rgb2 = hex_to_rgb(color2)

    l1 = get_luminance(*rgb1)
    l2 = get_luminance(*rgb2)

    lighter = max(l1, l2)
    darker = min(l1, l2)

    return (lighter + 0.05) / (darker + 0.05)


def is_dark_theme(image: Image.Image) -> bool:
    """Detect if image uses dark theme based on dominant colors"""
    # Resize for faster processing
    small = image.resize((100, 100))
    pixels = list(small.getdata())

    # Calculate average luminance
    total_luminance = 0
    for pixel in pixels:
        if len(pixel) >= 3:
            r, g, b = pixel[:3]
            total_luminance += get_luminance(r, g, b)

    avg_luminance = total_luminance / len(pixels)
    return avg_luminance < 0.5


def get_dominant_colors(image: Image.Image, num_colors: int = 5) -> List[Tuple[str, float]]:
    """Extract dominant colors from image"""
    # Resize for faster processing
    small = image.resize((100, 100))
    pixels = list(small.getdata())

    # Count color occurrences (simplified)
    color_counts = {}
    for pixel in pixels:
        if len(pixel) >= 3:
            r, g, b = pixel[:3]
            # Round to reduce unique colors
            r = (r // 16) * 16
            g = (g // 16) * 16
            b = (b // 16) * 16
            color = rgb_to_hex(r, g, b)
            color_counts[color] = color_counts.get(color, 0) + 1

    # Sort by frequency
    sorted_colors = sorted(color_counts.items(), key=lambda x: x[1], reverse=True)
    total_pixels = len(pixels)

    return [(color, count / total_pixels) for color, count in sorted_colors[:num_colors]]


def check_color_similarity(color1: str, color2: str, tolerance: int = 32) -> bool:
    """Check if two colors are similar within tolerance"""
    rgb1 = hex_to_rgb(color1)
    rgb2 = hex_to_rgb(color2)

    return all(abs(c1 - c2) <= tolerance for c1, c2 in zip(rgb1, rgb2))


def verify_dark_theme(image: Image.Image) -> Dict:
    """Verify image uses dark theme"""
    is_dark = is_dark_theme(image)
    dominant_colors = get_dominant_colors(image)

    result = {
        "check": "dark_theme",
        "passed": is_dark,
        "details": {
            "is_dark": is_dark,
            "dominant_colors": dominant_colors[:3],
        },
    }

    if not is_dark:
        result["recommendation"] = "Switch to dark theme for better eye comfort"

    return result


def verify_color_palette(image: Image.Image, theme: str = "dark") -> Dict:
    """Verify colors match design system palette"""
    dominant_colors = get_dominant_colors(image, 10)
    palette = DESIGN_SYSTEM["colors"][f"{theme}_theme"]

    matches = []
    for color, freq in dominant_colors:
        for name, expected in palette.items():
            if check_color_similarity(color, expected, tolerance=48):
                matches.append({
                    "detected": color,
                    "matches": name,
                    "expected": expected,
                    "frequency": f"{freq*100:.1f}%",
                })
                break

    match_percentage = len(matches) / len(dominant_colors) if dominant_colors else 0

    return {
        "check": "color_palette",
        "passed": match_percentage >= 0.5,
        "score": match_percentage * 100,
        "details": {
            "matches": matches,
            "dominant_colors": dominant_colors[:5],
        },
        "recommendation": "Align colors with design system palette" if match_percentage < 0.5 else None,
    }


def verify_contrast(image: Image.Image) -> Dict:
    """Verify contrast ratios meet WCAG standards"""
    dominant_colors = get_dominant_colors(image, 10)

    if len(dominant_colors) < 2:
        return {"check": "contrast", "passed": True, "note": "Not enough colors to verify"}

    # Find the pair of colors with highest contrast (bg vs text)
    # Assume the most dominant color is background
    bg_color = dominant_colors[0][0]

    # Find the color with highest contrast against background
    best_contrast = 0
    text_color = "#FFFFFF"

    for color, freq in dominant_colors[1:]:
        contrast = get_contrast_ratio(bg_color, color)
        if contrast > best_contrast:
            best_contrast = contrast
            text_color = color

    # If no good contrast found in dominant colors, check against expected text colors
    expected_text_colors = ["#F8FAFC", "#FFFFFF", "#E2E8F0"]
    for expected in expected_text_colors:
        contrast = get_contrast_ratio(bg_color, expected)
        if contrast > best_contrast:
            best_contrast = contrast
            text_color = expected

    contrast_ratio = best_contrast
    min_required = DESIGN_SYSTEM["contrast_requirements"]["normal_text"]

    return {
        "check": "contrast",
        "passed": contrast_ratio >= min_required,
        "score": min(100, (contrast_ratio / min_required) * 100),
        "details": {
            "contrast_ratio": round(contrast_ratio, 2),
            "required": min_required,
            "bg_color": bg_color,
            "text_color": text_color,
        },
        "recommendation": f"Increase contrast ratio to at least {min_required}:1" if contrast_ratio < min_required else None,
    }


def verify_screenshot(image_path: str) -> VerificationResult:
    """Run all verification checks on a screenshot"""
    image = Image.open(image_path)

    checks = []
    recommendations = []

    # Run checks
    dark_check = verify_dark_theme(image)
    checks.append(dark_check)
    if dark_check.get("recommendation"):
        recommendations.append(dark_check["recommendation"])

    theme = "dark" if dark_check["details"]["is_dark"] else "light"
    palette_check = verify_color_palette(image, theme)
    checks.append(palette_check)
    if palette_check.get("recommendation"):
        recommendations.append(palette_check["recommendation"])

    contrast_check = verify_contrast(image)
    checks.append(contrast_check)
    if contrast_check.get("recommendation"):
        recommendations.append(contrast_check["recommendation"])

    # Calculate overall score
    scores = [c.get("score", 100 if c["passed"] else 0) for c in checks]
    avg_score = sum(scores) / len(scores) if scores else 0

    all_passed = all(c["passed"] for c in checks)

    return VerificationResult(
        passed=all_passed,
        score=round(avg_score, 1),
        checks=checks,
        recommendations=recommendations,
    )


def verify_report(report_path: str) -> Dict:
    """Verify all screenshots in a test report"""
    with open(report_path, "r") as f:
        report = json.load(f)

    verification_results = {}

    for result in report.get("results", []):
        screenshot_path = result.get("screenshot_path")
        if screenshot_path and os.path.exists(screenshot_path):
            verification = verify_screenshot(screenshot_path)
            verification_results[result["test_id"]] = {
                "passed": verification.passed,
                "score": verification.score,
                "checks": verification.checks,
                "recommendations": verification.recommendations,
            }

    # Calculate summary
    total = len(verification_results)
    passed = sum(1 for v in verification_results.values() if v["passed"])
    avg_score = sum(v["score"] for v in verification_results.values()) / total if total > 0 else 0

    return {
        "summary": {
            "total": total,
            "passed": passed,
            "failed": total - passed,
            "average_score": round(avg_score, 1),
        },
        "results": verification_results,
    }


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print("Usage: python uiux-verifier.py <screenshot_or_report.json>")
        sys.exit(1)

    path = sys.argv[1]

    if path.endswith(".json"):
        # Verify report
        results = verify_report(path)
        print(json.dumps(results, indent=2, ensure_ascii=False))
    else:
        # Verify single screenshot
        try:
            result = verify_screenshot(path)
            print(f"\nVerification Results for: {path}")
            print(f"{'='*50}")
            print(f"Passed: {'Yes ✅' if result.passed else 'No ❌'}")
            print(f"Score: {result.score}/100")
            print(f"\nChecks:")
            for check in result.checks:
                status = "✅" if check["passed"] else "❌"
                print(f"  {status} {check['check']}")
            if result.recommendations:
                print(f"\nRecommendations:")
                for rec in result.recommendations:
                    print(f"  • {rec}")
        except Exception as e:
            print(f"Error: {e}")
            sys.exit(1)
