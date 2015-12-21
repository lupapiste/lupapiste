LUPAPISTE.StatementEditReplyModel = function(params) {
  "use strict";
  var self = this;

  self.tab = "reply";

  self.authModel = params.authModel;
  var applicationId = params.applicationId;
  var statementId = params.statementId;

  self.data = ko.observable();
  self.nothingToAdd = ko.observable();
  self.text = ko.observable();

  var dirty = ko.observable(false);
  var goingToSubmit = ko.observable(false);

  var saveDraftCommand = "save-statement-reply-as-draft";
  var submitCommand = "reply-statement";

  var submitAllowed = ko.pureComputed(function() {
    return (!!self.nothingToAdd() || !!self.text()) && !goingToSubmit();
  });

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(submitCommand);
  });

  self.isDraft = ko.pureComputed(function() {
    return _.contains(["replyable"], util.getIn(self.data, ["state"]));
  });

  self.replyVisible = ko.computed(function() {
    return  _.contains(["replied"], util.getIn(self.data, ["state"])) || self.authModel.ok(submitCommand);
  });

  self.text.subscribe(function(value) {
    if(util.getIn(self.data, ["reply", "text"]) !== value) {
      dirty(true);
    }
  });

  self.coverNote = ko.pureComputed(function() {
    return util.getIn(self.data, ["reply", "saateText"]) || "";
  });

  self.nothingToAdd.subscribe(function(value) {
    if(util.getIn(self.data, ["reply", "nothing-to-add"]) !== value) {
      dirty(true);
    }
  });

  submitAllowed.subscribe(function(value) {
    hub.send("statement::submitAllowed", {tab: self.tab, value: value});
  });

  hub.subscribe("statement::submit", function(params) {
    if(applicationId() === params.applicationId && statementId() === params.statementId && self.tab === params.tab) {
      goingToSubmit(true);
    }
  });

  function init(statement) {
    if (!dirty()) {
      self.text(util.getIn(statement, ["reply", "text"]));
      self.nothingToAdd(util.getIn(statement, ["reply", "nothing-to-add"]));
    }
  }

  function getCommandParams() {
    return {
      text: self.text(),
      "nothing-to-add": self.nothingToAdd()
    };
  }

  ko.utils.extend(self, new LUPAPISTE.StatementUpdate(_.extend( params, {
    data: self.data,
    dirty: dirty,
    goingToSubmit: goingToSubmit,
    saveDraftCommandName: saveDraftCommand,
    submitCommandName: submitCommand,
    init: init,
    getCommandParams: getCommandParams
  })));
};
