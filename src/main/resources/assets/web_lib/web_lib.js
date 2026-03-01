// Minimal weblib stub for config screen integration
window.weblib = (function(){
  const state = {
    logCssWarnings: false,
    logScriptCalls: false
  };

  function setFontSize(v){
    console.log('setFontSize', v);
    const el = document.getElementById('fontSizeError');
    const val = Number(v);
    if (isNaN(val) || val < 6 || val > 32){ if(el) el.style.display='block'; return; }
    if(el) el.style.display='none';
    document.documentElement.style.fontSize = val + 'px';
  }

  function toggleLogCssWarnings(){
    state.logCssWarnings = !state.logCssWarnings;
    console.log('logCssWarnings ->', state.logCssWarnings);
    const btn = document.getElementById('logCssWarningsBtn');
    if(btn) btn.textContent = state.logCssWarnings ? 'On' : 'Off';
  }

  function toggleLogScriptCalls(){
    state.logScriptCalls = !state.logScriptCalls;
    console.log('logScriptCalls ->', state.logScriptCalls);
    const btn = document.getElementById('logScriptCallsBtn');
    if(btn) btn.textContent = state.logScriptCalls ? 'On' : 'Off';
  }

  function setMaxHistory(v){
    console.log('setMaxHistory', v);
    const el = document.getElementById('maxHistoryError');
    const val = Number(v);
    if (isNaN(val) || val < 1 || val > 100){ if(el) el.style.display='block'; return; }
    if(el) el.style.display='none';
  }

  function setScrollSpeed(v){
    console.log('setScrollSpeed', v);
    const el = document.getElementById('scrollSpeedError');
    const val = Number(v);
    if (isNaN(val) || val < 1 || val > 50){ if(el) el.style.display='block'; return; }
    if(el) el.style.display='none';
  }

  function cancel(){ console.log('cancel'); }
  function save(){ console.log('save'); }

  return {
    setFontSize,
    toggleLogCssWarnings,
    toggleLogScriptCalls,
    setMaxHistory,
    setScrollSpeed,
    cancel,
    save
  };
})();
