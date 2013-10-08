if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.Screenmessage = new (function () {
  var self = this;

  self.messages = ko.observableArray([]);

  self.refresh = function() {
    ajax.query("screenmessages")
    .success(function(data) {
      self.messages(data.screenmessages);
    })
    .error(function(e) {
      self.messages([]);
      debug("Could not load screen messages", e);
    })
    .call();
  };


  self.add = function() {
    ajax.command("screenmessages-add", {fi : $("#add-text-fi").val(),
                                        sv : $("#add-text-sv").val()})
    .success(function(data) {
      self.refresh();
    })
    .error(function(e) {
      debug("Could not add screen message", e);
    })
    .call();
  };

  self.reset = function() {
    ajax.command("screenmessages-reset")
    .success(function(data) {
      self.refresh();
    })
    .error(function(e) {
      debug("Could not reset screen messages", e);
    })
    .call();
  };

})();
