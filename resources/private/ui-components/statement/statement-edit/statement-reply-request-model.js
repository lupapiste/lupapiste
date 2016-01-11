LUPAPISTE.StatementReplyRequestModel = function(params) {
  "use strict";
  var self = this;

  self.tab = "reply-request";

  self.authModel = params.authModel;
  
  self.applicationTitle = params.applicationTitle;
  self.data = params.data;

  self.text = ko.observable();

  var commands = params.commands;

  self.data.subscribe(function() {
    self.text(util.getIn(self.data, ["reply", "saateText"]));
  });
 
  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(commands["submit"]);
  });

  self.submitAuthorized = ko.pureComputed(function() {
    return self.authModel.ok("request-for-statement-reply");
  });

  self.text.subscribe(function(value) {
    if(value && util.getIn(self.data, ["reply", "saateText"]) !== value) { 
      hub.send("statement::changed", {tab: self.tab, path: ["reply", "saateText"], value: value});
    }
  });

};
