// jshint supernew:true
LUPAPISTE.Screenmessage = new (function () {
  "use strict";
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


  self.add = function(fi, sv) {
    ajax.command("screenmessages-add", {fi : fi, sv : sv})
    .success(function() {
      self.refresh();
    })
    .error(function(e) {
      debug("Could not add screen message", e);
    })
    .call();
  };

  self.reset = function() {
    ajax.command("screenmessages-reset")
    .success(function() {
      self.refresh();
    })
    .error(function(e) {
      debug("Could not reset screen messages", e);
    })
    .call();
  };

  self.hide = function(){
    $("#sys-notification").hide();
  };

})();
