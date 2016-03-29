var invites = (function() {
  "use strict";

  function getInvites(callback) {
    ajax.query("invites")
      .success(function(d) {
        callback(d);
      })
      .onError("error.unauthorized", _.noop)
      .call();
  }

  return {
    getInvites : getInvites
  };

})();
