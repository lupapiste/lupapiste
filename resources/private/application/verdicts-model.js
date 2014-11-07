LUPAPISTE.VerdictsModel = function() {
  "use strict";
  var self = this;

  function getApplicationId(bindings) {
    return bindings.application.id();
  }

  self.authorities = [];
  self.verdicts = ko.observable();
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.newProcessing = ko.observable(false);
  self.newPending = ko.observable(false);

  self.applicationId = null;

  self.refresh = function(application, authorities) {
    self.applicationId = application.id;
    var verdicts = _.map(_.cloneDeep(application.verdicts || []), function(verdict) {
      var paatokset = _.map(verdict.paatokset || [], function(paatos) {
        var poytakirjat = _.map(paatos.poytakirjat || [], function(pk) {
          var myAttachments = _.filter(application.attachments || [], function(attachment) {
            var target = attachment.target;
            return target && target.type === "verdict" && (target.urlHash ? target.urlHash === pk.urlHash : target.id === verdict.id);
          }) || [];
          pk.attachments = myAttachments;
          return pk;
        });
        paatos.poytakirjat = poytakirjat;
        paatos.signatures = verdict.signatures;
        paatos.verdict = verdict;
        return paatos;});
      verdict.paatokset = paatokset;
      return verdict;
    });

    self.verdicts(verdicts);
    self.authorities = authorities;
  };

  self.newVerdict = function() {
    ajax.command("new-verdict-draft", {id: self.applicationId})
      .processing(self.newProcessing)
      .pending(self.newPending)
      .success(function(d) {
        repository.load(self.applicationId, self.newPending, function(application) {
          LUPAPISTE.verdictPageController.setApplicationModelAndVerdictId(application, self.authorities, d.verdictId);
          window.location.hash = "!/verdict/" + self.applicationId + "/" + d.verdictId;
        });})
    .call();
    return false;
  };

  self.openVerdict = function(bindings, verdict) {
    var applicationId = getApplicationId(bindings);
    LUPAPISTE.verdictPageController.setApplicationModelAndVerdictId(bindings.application._js, self.authorities, verdict.id);
    window.location.hash = "!/verdict/" + applicationId + "/" + verdict.id;
    return false;
  };

  self.publishVerdict = function(bindings, verdict) {
    var applicationId = getApplicationId(bindings);
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("verdict.confirmpublish"), {title: loc("yes"), fn: function() {
      ajax.command("publish-verdict", {id: applicationId, verdictId: verdict.id})
        .success(function(d) {repository.load(applicationId, self.newPending);})
        .call();
      }});
  };

  self.deleteVerdict = function(bindings) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("verdict.confirmdelete"), {title: loc("yes"), fn: function() {
      ajax.command("delete-verdict", {id: self.applicationId, verdictId: bindings.id})
        .success(function(d) {repository.load(self.applicationId);})
        .call();
      }});
  };

  self.checkVerdict = function(bindings){
    var applicationId = getApplicationId(bindings);
    ajax.command("check-for-verdict", {id: applicationId})
    .processing(self.processing)
    .pending(self.pending)
    .success(function(d) {
      var content = loc("verdict.verdicts-found-from-backend", d.verdictCount, d.taskCount);
      LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict.fetch.title"), content);
      pageutil.showAjaxWait();
      repository.load(applicationId);
    })
    .error(function(d) {
      LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict.fetch.title"), loc(d.text));
    })
    .call();
  };

  self.verdictSigningModel = new LUPAPISTE.VerdictSigningModel("#dialog-sign-verdict");
  $(function() {
    $(self.verdictSigningModel.dialogSelector).applyBindings({verdictSigningModel: self.verdictSigningModel});
  });

  self.openSigningDialog = function(paatos) {
    self.verdictSigningModel.init(self.applicationId, paatos.verdict.id);
  };

  self.verdictSignedByUser = function(paatos) {
    return _.some(paatos.signatures, {user: {id: currentUser.id()}});
  };
};
