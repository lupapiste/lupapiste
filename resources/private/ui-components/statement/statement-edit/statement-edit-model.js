LUPAPISTE.StatementEditModel = function(params) {
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var submitCommand = params.submitCommand;
  var saveDraftCommand = params.saveDraftCommand;
  
  self.authModel = params.authModel;
  self.tab = params.tabName;

  self.data = ko.observable();
  self.application = params.application;

  self.statuses = ko.observableArray([]);
  self.selectedStatus = ko.observable();
  self.nothingToAdd = ko.observable();
  self.text = ko.observable();
  self.submit = ko.observable(false);
  self.saving = ko.observable(false);
  self.dirty = ko.observable(false);
  self.modifyId = ko.observable(util.randomElementId());

  self.isEditable = function(field) {
    if (self.tab === "statement") {
      return _.contains(["text", "status"], field) && self.authModel.ok(submitCommand);
    } else {
      return _.contains(["text", "nothing-to-add"], field) && self.authModel.ok(submitCommand);
    }
  }
  
  self.isDraft = ko.pureComputed(function() {
    if (self.tab === "statement") {
      return _.contains(["requested", "draft"], util.getIn(self.data, ["state"]));
    } else {
      return _.contains(["announced"], util.getIn(self.data, ["state"])); 
    }
  });

  self.coveringNote = ko.pureComputed(function() {
    var isStatementGiver = util.getIn(self.data(), ["person", "userId"]) === lupapisteApp.models.currentUser.id();
    return self.tab === "statement" && isStatementGiver ? util.getIn(self.data, ["saateText"]) : "";
  });

  var draftTimerId = undefined;

  self.text.subscribe(function(value) {
    if(util.getIn(self.data(), ["text"]) !== value) { 
      self.dirty(true);
    }
  });

  self.selectedStatus.subscribe(function(value) {
    if(util.getIn(self.data(), ["status"]) !== value) {
      self.dirty(true);
    }
  });

  self.application.subscribe(function(application) {
    var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId(); });
    if(statement) {
      if (!statement["modify-id"]) {
        statement["modify-id"] = "";
      }
      self.data(ko.mapping.fromJS(statement));

      if (!self.dirty()) {
        self.selectedStatus(statement.status);  // LUPA-482 part II
        self.text(util.getIn(statement, self.tab === "statement" ? ["text"] : ["reply", "text"]));
        self.nothingToAdd(util.getIn(statement, ["reply", "nothing-to-add"]));
        self.dirty(false);
      }

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

    } else {
      pageutil.openPage("404");
    }
  });

  hub.subscribe("statement::give-statement", function(params) {
    if(applicationId() === params.applicationId && statementId() === params.statementId) {
      clearTimeout(draftTimerId);
      self.submit(true); 
    }
  });

  self.doSubmit = ko.computed(function() {
    return !self.saving() && self.submit();
  });

  function getCommandParams() {
    return _.extend({ 
      id: applicationId(), 
      "modify-id": self.modifyId(),
      "prev-modify-id": util.getIn(self.data(), ["modify-id"], ""),
      statementId: statementId(), 
      lang: loc.getCurrentLanguage(),
      text: self.text()
    }, 
      self.tab === "statement" ? {status: self.selectedStatus()} : {"nothing-to-add": self.nothingToAdd()}
    );
  }

  self.doSubmit.subscribe(function(doSubmit) {
    if (doSubmit) {
      self.saving(true);
      ajax
        .command(submitCommand, getCommandParams())
        .success(function() {
          updateModifyId(self);
          pageutil.openApplicationPage({id: applicationId()}, "statement");
          repository.load(applicationId());
          hub.send("indicator-icon", {clear: true});
          hub.send("indicator", {style: "positive"});
          return false;
        })
        .error(function() {
          hub.send("indicator", {style: "negative"});
        })
        .fail(function() {
          hub.send("indicator", {style: "negative"});
        })
        .complete(function() {
          self.submit(false);
          self.saving(false);
        })
        .call();
    }
  });

  var submitAllowed = ko.pureComputed(function() {
    return !!self.selectedStatus() && !!self.text() && !self.submit();
  });

  submitAllowed.subscribe(function(value) {
    hub.send("statement::submitAllowed", {value: value});
  });

  self.dirty.subscribe(function(dirty) {
    clearTimeout(draftTimerId);
    if (dirty) {
      draftTimerId = _.delay(_.partial(updateDraft, self), 2000);
    }
  });

  function updateModifyId(self) {
    self.data()["modify-id"](self.modifyId());
    self.modifyId(util.randomElementId());
  }

  function updateDraft(self) {
    if (self.dirty()) {
      self.saving(true);
      self.dirty(false);
      ajax
        .command(saveDraftCommand, getCommandParams())
        .success(function() {
          updateModifyId(self);
          hub.send("indicator-icon", {style: "positive"});
          return false;
        })
        .complete(function() { self.saving(false); })
        .call();
    }
    return false;
  };

};