(() => {
  const i18nNode = document.querySelector('.hub-recognition-messages');
  if (!i18nNode) {
    return;
  }

  const msgs = {
    success: i18nNode.dataset.success || '',
    genericError: i18nNode.dataset.genericError || '',
    selfError: i18nNode.dataset.selfError || '',
    dailyError: i18nNode.dataset.dailyError || '',
    cooldownError: i18nNode.dataset.cooldownError || '',
    disabledError: i18nNode.dataset.disabledError || '',
    invalidError: i18nNode.dataset.invalidError || '',
    duplicateError: i18nNode.dataset.duplicateError || '',
  };

  const reasonToMessage = (reason) => {
    switch (reason) {
      case 'recognition_self_not_allowed':
        return msgs.selfError;
      case 'recognition_daily_limit_reached':
        return msgs.dailyError;
      case 'recognition_cooldown_active':
        return msgs.cooldownError;
      case 'recognition_disabled':
        return msgs.disabledError;
      case 'recognition_invalid_payload':
        return msgs.invalidError;
      case 'recognition_already_recorded':
        return msgs.duplicateError;
      default:
        return msgs.genericError;
    }
  };

  const setFeedback = (actions, text, ok) => {
    const feedback = actions.querySelector('.hub-recognition-feedback');
    if (!feedback) {
      return;
    }
    feedback.textContent = text || '';
    feedback.classList.toggle('is-ok', ok);
    feedback.classList.toggle('is-error', !ok);
  };

  const lockButtons = (actions, locked) => {
    actions.querySelectorAll('.hub-recognition-btn').forEach((button) => {
      button.disabled = locked;
    });
  };

  document.addEventListener('click', async (event) => {
    const button = event.target.closest('.hub-recognition-btn');
    if (!button) {
      return;
    }
    const actions = button.closest('.hub-recognition-actions');
    if (!actions) {
      return;
    }

    const targetUserId = actions.dataset.recognitionTarget;
    const sourceObjectType = actions.dataset.sourceObjectType;
    const sourceObjectId = actions.dataset.sourceObjectId;
    const recognitionType = button.dataset.recognitionType;

    if (!targetUserId || !sourceObjectType || !sourceObjectId || !recognitionType) {
      setFeedback(actions, msgs.invalidError, false);
      return;
    }

    setFeedback(actions, '', true);
    lockButtons(actions, true);
    try {
      const response = await fetch('/api/community/reputation/recognitions', {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          target_user_id: targetUserId,
          source_object_type: sourceObjectType,
          source_object_id: sourceObjectId,
          recognition_type: recognitionType,
        }),
      });

      let payload = {};
      try {
        payload = await response.json();
      } catch (_ignored) {
        payload = {};
      }

      if (response.ok) {
        setFeedback(actions, msgs.success, true);
        return;
      }
      setFeedback(actions, reasonToMessage(payload.error), false);
    } catch (_err) {
      setFeedback(actions, msgs.genericError, false);
    } finally {
      lockButtons(actions, false);
    }
  });
})();
