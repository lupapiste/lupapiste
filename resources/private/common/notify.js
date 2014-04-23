var notify = (function() {
  "use strict";

  var level = {
    DEBUG : 'DEBUG',
    DEPLOY : 'DEPLOY'
  };
  var env = {
    level : level.DEPLOY
  };

  function setLevel(level) {
    env.level = level;
  }

  function success(title, data) {
    debug(title, data);
  }

  function error(title, data) {
    LUPAPISTE.ModalDialog.close();
    LUPAPISTE.ModalDialog.showDynamicOk(title, data);
  }

  function info(title, data) {
    debug(title, data);
  }

  return {
    info : info,
    error : error,
    success : success,
    level : level,
    setLevel : setLevel
  };

})();
