var vetuma = (function() {
  "use strict";

  function wrapHandlers(onFound, onNotFound, onEidasFound) {
    return function (resp) {
      if(resp && resp.userid){
        return onFound(resp);
      } else if (resp && resp.eidasId){
        return onEidasFound(resp);
      } else {
        return onNotFound(resp);
      }
    };
  }

  function getUser(onFound, onNotFound, onEidasFound, onError) {
    ajax.get("/api/vetuma/user").raw(true).success(wrapHandlers(onFound, onNotFound, onEidasFound)).error(onError).call();
  }

  function logoutUser(onSuccess) {
    ajax.deleteReq("/api/vetuma/user").raw(true).success(onSuccess).call();
  }

  return {
    getUser: getUser,
    logoutUser: logoutUser
  };

})();
