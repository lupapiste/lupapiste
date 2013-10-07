if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.Screenmessage = function () {
  var self = this;

  self.messages = ko.observableArray([]);

  self.refresh = function(application) {
    console.log("messagesModel refresh");

    ajax.get("/system/screenmessage")
    //.raw(false)
    .success(function(data) {
      console.log("/system/screenmessage Success, data: ", data);
      self.messages(data);
    })
    .error(function(e) {
      self.messages([]);
      debug("Could not load screen messages", e);
    })
    .call();
  };
}
