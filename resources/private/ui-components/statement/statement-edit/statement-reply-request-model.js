LUPAPISTE.StatementReplyRequestModel = function(params) {
  "use strict";
  var self = this;

  self.tab = "reply-request";

  self.authModel = params.authModel;
  var applicationId = params.applicationId;
  var statementId = params.statementId;

  self.data = ko.observable();
  self.statuses = ko.observableArray([]);
  self.selectedStatus = ko.observable();
  self.text = ko.observable();

  var dirty = ko.observable(false);
  var goingToSubmit = ko.observable(false);

  var submitCommand = "request-for-statement-reply";

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(submitCommand);
  });

  hub.send("statement::submitAllowed", {tab: self.tab, value: true});

  hub.subscribe("statement::submit", function(params) {
    if(applicationId() === params.applicationId && statementId() === params.statementId && self.tab === params.tab) {
      goingToSubmit(true);
    }
  });

  function getCommandParams() {
    return {
      text: self.text()
    };
  }

  ko.utils.extend(self, new LUPAPISTE.StatementUpdate(_.extend( params, {
    data: self.data,
    dirty: dirty,
    goingToSubmit: goingToSubmit,
    submitCommandName: submitCommand,
    init: _.noop,
    getCommandParams: getCommandParams
  })));
};
