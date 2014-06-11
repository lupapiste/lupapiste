LUPAPISTE.VerdictsModel = function() {
  "use strict";
  var self = this;

  function getApplicationId(bindings) {
    return bindings.application.id();
  }

  self.verdicts = ko.observable();
  self.processing = ko.observable(false);
  self.pending = ko.observable(false);

  self.newProcessing = ko.observable(false);
  self.newPending = ko.observable(false);

  self.refresh = function(application) {
    var verdicts = _.map(_.cloneDeep(application.verdicts || []), function(verdict) {

      var paatokset = _.map(verdict.paatokset || [], function(paatos) {
        var poytakirjat = _.map(paatos.poytakirjat || [], function(pk) {
          var myId = {type: "verdict", id: verdict.id, urlHash: pk.urlHash};
          var myAttachments = _.filter(application.attachments || [], function(attachment) {return _.isEqual(attachment.target, myId);}) || [];
          pk.attachments = myAttachments;
          return pk;
        });
        paatos.poytakirjat = poytakirjat;
        return paatos;});
      verdict.paatokset = paatokset;
      return verdict;
    });

    self.verdicts(verdicts);
  };

  self.newVerdict = function(bindings) {
    var applicationId = getApplicationId(bindings);
    ajax.command("new-verdict-draft", {id: applicationId})
      .processing(self.newProcessing)
      .pending(self.newPending)
      .success(function(d) {
        repository.load(applicationId, self.newPending, function(application) {
          verdictPageController.setApplicationModelAndVerdictId(application, d.verdictId);
          window.location.hash = "!/verdict/" + applicationId + "/" + d.verdictId;
        });})
    .call();
    return false;
  };

  // TODO rewrite
  /*
  self.openVerdict = function(bindings) {
    window.location.hash = "!/verdict/" + getApplicationId(bindings);
    return false;
  };
  */

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
    .call();
  };
};
