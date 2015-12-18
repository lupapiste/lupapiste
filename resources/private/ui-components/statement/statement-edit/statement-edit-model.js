LUPAPISTE.StatementEditModel = function(params) {
  var self = this;

  self.tab = "statement";

  self.authModel = params.authModel;
  var applicationId = params.applicationId;
  var statementId = params.statementId;

  self.data = ko.observable();
  self.statuses = ko.observableArray([]);
  self.selectedStatus = ko.observable();
  self.text = ko.observable();

  var dirty = ko.observable(false);
  var goingToSubmit = ko.observable(false);
 
  var saveDraftCommand = "save-statement-as-draft";
  var submitCommand = "give-statement";

  var submitAllowed = ko.pureComputed(function() {
    return !!self.selectedStatus() && !!self.text() && !goingToSubmit();
  });

  self.enabled = ko.pureComputed(function() {
    return self.authModel.ok(submitCommand);
  });
  
  self.isDraft = ko.pureComputed(function() {
    return _.contains(["requested", "draft"], util.getIn(self.data, ["state"]));
  });

  self.coverNote = ko.pureComputed(function() {
    var isStatementGiver = util.getIn(self.data, ["person", "userId"]) === lupapisteApp.models.currentUser.id();
    return self.tab === "statement" && isStatementGiver ? util.getIn(self.data, ["saateText"]) : "";
  });

  self.text.subscribe(function(value) {
    if(util.getIn(self.data, ["text"]) !== value) { 
      dirty(true);
    }
  });

  self.selectedStatus.subscribe(function(value) {
    if(util.getIn(self.data, ["status"]) !== value) {
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
      self.text(util.getIn(statement, ["text"]));
      self.selectedStatus(util.getIn(statement, ["status"]));
    }
    if (_.isEmpty(self.statuses()) && applicationId()) {
      self.statuses.push("");
      ajax
        .query("get-possible-statement-statuses", {id: applicationId()})
        .success(function(resp) {
          var sorted = _(resp.data)
            .map(function(item) { return {id: item, name: loc(["statement", item])}; })
            .sortBy("name")
            .value();
          self.statuses(sorted);
        })
        .call();
    }
  }

  function getCommandParams() {
    return {
      text: self.text(),
      status: self.selectedStatus()
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
}