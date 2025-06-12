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

  self.hide = function(){
    $("#sys-notification").hide();
  };

})();
