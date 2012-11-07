/*
 * notify.js:
 */

var notify = function() {

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

  function getMessage(type, title, data) {
    var msg = {
      type : type,
      title : title,
      animate_speed : 1000,
      delay : 5000
    };
    if (env.level === level.DEBUG) {
      msg.text = JSON.stringify(data);
    }
    return msg;
  }

  function success(title, data) {
    debug(title, data);
    $.pnotify(getMessage('success', title, data));
  }

  function error(title, data) {
    debug(title, data);
    $.pnotify(getMessage('error', title, data));
  }

  function info(title, data) {
    debug(title, data);
    $.pnotify(getMessage('info', title, data));
  }

  return {
    info : info,
    error : error,
    success : success,
    level : level,
    setLevel : setLevel
  };

}();
