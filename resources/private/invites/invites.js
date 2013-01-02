var invites = function() {

  function getInvites(callback) {
    ajax.query("invites")
      .success(function(d) {
        callback(d);
      })
      .call();
  }

  return {
    getInvites : getInvites
  };

}();
