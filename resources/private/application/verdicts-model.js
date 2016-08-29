LUPAPISTE.VerdictsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

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

  function poytakirjaAttachments( verdict, pk ) {
    return self.disposedComputed( function() {
      return _.filter(lupapisteApp.services.attachmentsService.attachments(),
                      function(attachment) {
                        var target = attachment().target;
                        var idMatch = false;
                        if (target && target.type === "verdict") {
                          if (target.poytakirjaId) {
                            idMatch = target.poytakirjaId === pk.id;
                          } else if (target.urlHash) {
                            idMatch = target.urlHash === pk.urlHash;
                          } else {
                            idMatch = target.id === verdict.id;
                          }
                        }
                        return idMatch;
                      });
    });
  }

  self.refresh = function(application, authorities) {
    self.applicationId = application.id;
    var verdicts = _(application.verdicts || [])
      .cloneDeep()
      .map(function(verdict) {
        var paatokset = _.map(verdict.paatokset || [], function(paatos) {
          var poytakirjat = _.map(paatos.poytakirjat || [], function(pk) {
            pk.attachments = poytakirjaAttachments( verdict, pk );
            return pk;
          });
          paatos.poytakirjat = poytakirjat;
          paatos.signatures = verdict.signatures;
          paatos.verdict = verdict;
          return paatos;
        });
        verdict.paatokset = paatokset;

        var pk = util.getIn(paatokset, [0, "poytakirjat", 0]) || {};
        var dates = util.getIn(paatokset, [0, "paivamaarat"]) || {};
        verdict.canBePublished = verdict.kuntalupatunnus && pk.status && pk.paatoksentekija && dates.anto && dates.lainvoimainen;

        return verdict;
      })
      .filter(function(v) {return !_.isEmpty(v.paatokset);});

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
          pageutil.openPage("verdict", self.applicationId + "/" + d.verdictId);
        });})
    .call();
    return false;
  };

  self.openVerdict = function(bindings, verdict) {
    var applicationId = getApplicationId(bindings);
    LUPAPISTE.verdictPageController.setApplicationModelAndVerdictId(bindings.application._js, self.authorities, verdict.id);
    pageutil.openPage("verdict", applicationId + "/" + verdict.id);
    return false;
  };

  self.publishVerdict = function(bindings, verdict) {
    var applicationId = getApplicationId(bindings);
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("verdict.confirmpublish"), {title: loc("yes"), fn: function() {
      ajax.command("publish-verdict", {id: applicationId, verdictId: verdict.id, lang: loc.getCurrentLanguage()})
        .success(function() {repository.load(applicationId, self.newPending);})
        .call();
      hub.send("track-click", {category:"Application", label: "", event:"publishVerdict"});
      }});
  };

  self.deleteVerdict = function(bindings) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"), loc("verdict.confirmdelete"), {title: loc("yes"), fn: function() {
      ajax.command("delete-verdict", {id: self.applicationId, verdictId: bindings.id})
        .success(function() {repository.load(self.applicationId);})
        .call();
        hub.send("track-click", {category:"Application", label: "", event:"deleteVerdict"});
      }});
  };

  self.checkVerdict = function(bindings){
    function doCheckVerdict() {
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
      hub.send("track-click", {category:"Application", label: "", event:"chechForVerdict"});
    }
    if( lupapisteApp.services.verdictAppealService.hasAppeals()) {
      LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"),
                                             loc("verdict.confirmfetch"),
                                             {title: loc("yes"),
                                              fn: doCheckVerdict});
    } else {
      doCheckVerdict();
    }
  };

  self.verdictSigningModel = new LUPAPISTE.VerdictSigningModel("#dialog-sign-verdict");
  $(function() {
    $(self.verdictSigningModel.dialogSelector).applyBindings({verdictSigningModel: self.verdictSigningModel});
  });

  self.openSigningDialog = function(paatos) {
    self.verdictSigningModel.init(self.applicationId, paatos.verdict.id);
  };

  self.verdictSignedByUser = function(paatos) {
    hub.send("track-click", {category:"Application", label: "", event:"verdictSignedByUser"});
    return _.some(paatos.signatures, {user: {id: lupapisteApp.models.currentUser.id()}});
  };
};
