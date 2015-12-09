LUPAPISTE.StatementEditModel = function(params) {
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  
  self.authModel = params.authModel;

  self.data = ko.observable();
  self.application = ko.observable();

  self.statuses = ko.observableArray([]);
  self.selectedStatus = ko.observable();
  self.text = ko.observable();
  self.submit = ko.observable(false);
  self.saving = ko.observable(false);
  self.dirty = ko.observable(false);
  self.modifyId = ko.observable(util.randomElementId());
  
  self.isDraft = ko.computed(function() {
    return _.contains(["requested", "draft"], util.getIn(self.data, ["state"]));
  });

  var draftTimerId = undefined;

  self.text.subscribe(function(value) {
    if(util.getIn(self.data(), ["text"])  !== value) { 
      self.dirty(true);
    }
  });

  self.selectedStatus.subscribe(function(value) {
    if(util.getIn(self.data(), ["status"]) !== value) {
      self.dirty(true);
    }
  });

  self.clear = function() {
    self.data(null);
    self.application(null);
    self.statuses([]);
    self.selectedStatus(null);
    self.text(null);
    self.dirty(false);
    return self;
  };

  function refresh(application) {
    self.application(ko.mapping.fromJS(application));
    var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId(); });
    if(statement) {
      if (!statement["modify-id"]) {
        statement["modify-id"] = "";
      }
      self.data(ko.mapping.fromJS(statement));

      if (!self.dirty()) {
        if (statement.status) {
          self.selectedStatus(statement.status);  // LUPA-482 part II
        }
        if (statement.text) {
          self.text(statement.text);
        }
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
  };

  self.openDeleteDialog = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("statement.delete.header"),
        loc("statement.delete.message"),
        {title: loc("yes"), fn: deleteStatementFromServer},
        {title: loc("no")}
      );
  };

  hub.subscribe("statement::give-statement", function(params) {
    if(applicationId() === params.applicationId && statementId() === params.statementId) {
      clearTimeout(draftTimerId);
      self.submit(true); 
    }
  });

  self.doSubmit = ko.computed(function() {
    return !self.saving() && self.submit();
  });

  self.doSubmit.subscribe(function(doSubmit) {
    if (doSubmit) {
      self.saving(true);
      ajax
        .command("give-statement", {
          id: applicationId(), 
          "modify-id": self.modifyId(),
          "prev-modify-id": util.getIn(self.data(), ["modify-id"], ""),
          statementId: statementId(), 
          status: self.selectedStatus(), 
          text: self.text(), 
          lang: loc.getCurrentLanguage()
        })
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

  self.canDeleteStatement = function() {
    return self.authModel.ok("delete-statement");
  };

  function updateModifyId(self) {
    self.data()["modify-id"](self.modifyId());
    self.modifyId(util.randomElementId());
  }

  function updateDraft(self) {
    if (self.dirty()) {
      self.saving(true);
      self.dirty(false);
      ajax
        .command("save-statement-as-draft", {
          id: applicationId(), 
          "modify-id": self.modifyId(),
          "prev-modify-id": util.getIn(self.data(), ["modify-id"], ""),
          statementId: statementId(), 
          status: self.selectedStatus(), 
          text: self.text(), 
          lang: loc.getCurrentLanguage()
        })
        .success(function() {
          updateModifyId(self);
          hub.send("indicator-icon", {style: "positive"});
          return false;
        })
        .error(function() {
          hub.send("indicator-icon", {style: "negative"});
        })
        .fail(function() {
          hub.send("indicator-icon", {style: "negative"});
        })
        .complete(function() { self.saving(false); })
        .call();
    }
    return false;
  };

  function deleteStatementFromServer() {
    ajax
      .command("delete-statement", {id: applicationId(), statementId: statementId()})
      .success(function() {
        repository.load(applicationId());
        pageutil.openApplicationPage({id: applicationId()}, "statement");
        return false;
      })
      .call();
    return false;
  }

  repository.loaded(["statement"], function(application) {
    if (applicationId() === application.id) {
      self.authModel.refresh(application, {statementId: statementId()}, function() {
        refresh(application);
      });
    }
  });

};