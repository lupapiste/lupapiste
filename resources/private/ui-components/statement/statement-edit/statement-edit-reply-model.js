LUPAPISTE.StatementEditReplyModel = function(params) {
  "use strict";
  var self = this;

  self.tab = "reply";

  self.authModel = params.authModel;

  self.applicationTitle = params.applicationTitle;
  self.data = params.data;

  self.text = ko.observable();
  self.nothingToAdd = ko.observable();
  
  var commands = params.commands;

  self.data.subscribe(function() {
    self.nothingToAdd(util.getIn(self.data, ["nothing-to-add"]));
    self.text(util.getIn(self.data, ["reply", "text"]));
  });

  var submitAllowed = ko.pureComputed(function() {
    return !!self.nothingToAdd() || !!self.text() && !_.isEmpty(self.text());
  });

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(commands["submit"]);
  });

  self.isDraft = ko.pureComputed(function() {
    return _.contains(["replyable"], util.getIn(self.data, ["state"]));
  });

  self.replyVisible = ko.computed(function() {
    return _.contains(["replied"], util.getIn(self.data, ["state"])) || self.authModel.ok(commands["submit"]);
  });

  self.text.subscribe(function(value) {
    if(value && util.getIn(self.data, ["reply", "text"]) !== value) { 
      hub.send("statement::changed", {tab: self.tab, path: ["reply", "text"], value: value});
    }
  });

  self.coverNote = ko.pureComputed(function() {
    return util.getIn(self.data, ["reply", "saateText"]) || "";
  });

  self.nothingToAdd.subscribe(function(value) {
    if(value && util.getIn(self.data, ["reply", "nothing-to-add"]) !== value) {
      hub.send("statement::changed", {tab: self.tab, path: ["reply", "nothing-to-add"], value: value});
    }
  });

  submitAllowed.subscribe(function(value) {
    hub.send("statement::submitAllowed", {tab: self.tab, value: value});
  });

};
