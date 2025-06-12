var vetuma = (function() {
  "use strict";

  function wrapHandlers(onFound, onNotFound) {
    return function (resp) {
      // userid = registration with Finnish person id
      // eidasId = foreign registration without person id
      if (resp && (resp.userid || resp.eidasId)) {
        return onFound(resp);
      } else {
        return onNotFound(resp);
      }
    };
  }

  function getUser(onFound, onNotFound, onError) {
    ajax.get("/api/vetuma/user").raw(true).success(wrapHandlers(onFound, onNotFound)).error(onError).call();
  }

  function logoutUser(onSuccess) {
    ajax.deleteReq("/api/vetuma/user").raw(true).success(onSuccess).call();
  }

  return {
    getUser: getUser,
    logoutUser: logoutUser
  };

})();
