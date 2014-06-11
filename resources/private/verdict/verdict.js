var verdictPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var currentApplication = null;
  var currentVerdictId = null;

  function VerdictEditModel() {
    var self = this;

    self.applicationId = ko.observable();
    self.applicationTitle = ko.observable();

    self.processing = ko.observable();

    self.statuses = _.range(1,43); // 42 different values in verdict in krysp (verdict.clj)

    self.backendId = ko.observable();
    self.draft = ko.observable();
    self.status = ko.observable();
    self.name = ko.observable();
    self.text = ko.observable();
    self.agreement = ko.observable(false);
    self.section = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    self.reset = function(verdict) {
      var paatos = verdict.paatokset[0];
      var pk = paatos.poytakirjat[0];
      var dates = paatos.paivamaarat;

      self.backendId(verdict.kuntalupatunnus);
      self.draft(verdict.draft);
      self.status(pk.status);
      self.name(pk.paatoksentekija);
      self.given(dates.anto);
      self.official(dates.lainvoimainen);
      self.text(pk.paatos);
      self.agreement(verdict.sopimus);
      self.section(pk.pykala);
    };

    self.refresh = function(application, verdictId) {
      //var app = ko.mapping.toJS(application);
      self.applicationId(application.id);
      self.applicationTitle(application.title);

      var verdict = _.find((application.verdicts || []), function (v) {return v.id === verdictId;});
      if (verdict) {
        self.reset(verdict);
      } else {
        history.back();
        repository.load(application.id);
      }
    };

    self.save = function(onSuccess) {
      var givenMillis = new Date(self.given()).getTime();
      var officialMillis = new Date(self.official()).getTime();
      ajax
        .command("save-verdict-draft",
          {id: currentApplicationId, verdictId: currentVerdictId,
           backendId: self.backendId(), status: self.status(),
           name: self.name(), text: self.text(),
           section: self.section(),
           agreement: self.agreement() || false,
           given: givenMillis, official: officialMillis})
        .success(onSuccess)
        .processing(self.processing)
        .call();
      return false;
    };

    self.submit = function() {
      self.save(function() {
        repository.load(currentApplicationId);
        LUPAPISTE.ModalDialog.showDynamicOk(loc("form.saved"), loc("saved"));
      });
    };

    self.publish = function() {
      self.save(function() {
        ajax
        .command("publish-verdict", {id: currentApplicationId, verdictId: currentVerdictId})
        .success(function() {
          repository.load(currentApplicationId);
          window.location.hash = "!/application/" + currentApplicationId + "/verdict";
        })
        .processing(self.processing)
        .call();
      });
    };

    self.disabled = ko.computed(function() {
      return self.processing() || !(self.backendId() && self.status() && self.name() && self.given() && self.official());
    });
  }

  var verdictModel = new VerdictEditModel();
  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({}, "muut.muu");

  function refresh(application, verdictId) {
    currentApplication = application;
    currentApplicationId = currentApplication.id;
    currentVerdictId = verdictId;

    authorizationModel.refresh(application);
    verdictModel.refresh(application, verdictId);
    attachmentsModel.refresh(application, {type: "verdict", id: verdictId});
  }

  repository.loaded(["verdict"], function(application) {
    if (currentApplicationId === application.id) {
      refresh(application, currentVerdictId);
    }
  });

  hub.onPageChange("verdict", function(e) {
    var applicationId = e.pagePath[0];
    currentVerdictId = e.pagePath[1];
    // Reload application only if needed
    if (currentApplicationId !== applicationId) {
      repository.load(applicationId);
    } else {
      refresh(currentApplication, currentVerdictId);
    }
    currentApplicationId = applicationId;
  });

  $(function() {
    $("#verdict").applyBindings({
      verdictModel: verdictModel,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel
    });
  });

  return {
    setApplicationModelAndVerdictId: refresh
  };

})();
