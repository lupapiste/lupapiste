LUPAPISTE.StatementUpdate = function(params) {
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var application = params.application;
  var data = params.data;
  var dirty = params.dirty;
  var goingToSubmit = params.goingToSubmit;

  var saveDraftCommand = params.saveDraftCommandName;
  var submitCommand = params.submitCommandName;

  var init = params.init;
  var getCommandParams = params.getCommandParams;

  var saving = ko.observable(false);
  var modifyId = ko.observable(util.randomElementId());

  var draftTimerId = undefined;

  var doSubmit = ko.pureComputed(function() {
    return !saving() && goingToSubmit();
  });

  application.subscribe(function(application) {
    var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId(); });
    if(statement) {
      if (!statement["modify-id"]) {
        statement["modify-id"] = "";
      }
      data(ko.mapping.fromJS(statement));
      init(statement);

    } else {
      pageutil.openPage("404");
    }
  });

  function updateModifyId() {
    data()["modify-id"](modifyId());
    modifyId(util.randomElementId());
  }

  doSubmit.subscribe(function(doSubmit) {
    if (doSubmit) {
      clearTimeout(draftTimerId);
      saving(true);
      ajax
        .command(submitCommand, _.extend({
          id: applicationId(), 
          "modify-id": modifyId(),
          "prev-modify-id": util.getIn(data(), ["modify-id"], ""),
          statementId: statementId(), 
          lang: loc.getCurrentLanguage()
        }, getCommandParams()))
        .success(function() {
          updateModifyId();
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
          goingToSubmit(false);
          saving(false);
        })
        .call();
    }
  });

  function updateDraft() {
    saving(true);
    dirty(false);
    ajax
      .command(saveDraftCommand, _.extend({
        id: applicationId(), 
        "modify-id": modifyId(),
        "prev-modify-id": util.getIn(data(), ["modify-id"], ""),
        statementId: statementId(), 
        lang: loc.getCurrentLanguage()
      }, getCommandParams()))
      .success(function() {
        updateModifyId();
        hub.send("indicator-icon", {style: "positive"});
        return false;
      })
      .complete(function() {
        saving(false);
      })
      .call();
    return false;
  };

  dirty.subscribe(function(dirty) {
    clearTimeout(draftTimerId);
    if (dirty) {
      draftTimerId = _.delay(updateDraft, 2000);
    }
  });
};