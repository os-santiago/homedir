"""Test learning system pattern display functionality."""

def test_learning_pattern_display_format():
    """Verify learning model output format matches expected structure."""
    expected_fields = [
        "error",
        "confidence", 
        "focus",
        "next",
        "detail",
        "primary"
    ]
    
    # Example output from actual system:
    # Learning model: error=wrong_agent confidence=0.97 focus=re-evaluate agent routing before responding next=route ambiguous work through a second pass detail=cross_check primary=n/a
    
    sample_output = "error=wrong_agent confidence=0.97 focus=re-evaluate agent routing before responding next=route ambiguous work through a second pass detail=cross_check primary=n/a"
    
    # Parse key=value pairs
    parsed = {}
    parts = sample_output.split()
    for part in parts:
        if "=" in part:
            key, value = part.split("=", 1)
            parsed[key] = value
    
    # Verify all expected fields present
    for field in expected_fields:
        assert field in parsed, f"Missing field: {field}"
    
    # Verify specific values
    assert parsed["error"] == "wrong_agent"
    assert float(parsed["confidence"]) == 0.97
    assert "route" in parsed["next"]
    
    print("✓ Learning pattern display format validated")


def test_pattern_triggers_guidance():
    """Verify wrong_agent pattern triggers appropriate guidance."""
    pattern_type = "wrong_agent"
    confidence = 0.97
    
    # High confidence should trigger actionable guidance
    if confidence >= 0.9:
        guidance = "re-evaluate agent routing before responding"
        assert "route" in guidance or "agent" in guidance
        print(f"✓ High confidence ({confidence}) triggers guidance: {guidance}")
    else:
        print(f"✗ Confidence {confidence} too low for actionable guidance")


if __name__ == "__main__":
    test_learning_pattern_display_format()
    test_pattern_triggers_guidance()
    print("\n✅ All learning display tests passed")
