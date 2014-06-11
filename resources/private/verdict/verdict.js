var verdictPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var currentVerdictId = null;

  function VerdictEditModel() {
    var self = this;

    self.application = ko.observable();

    self.statuses = _.range(1,43); // 42 different values in verdict in krysp (verdict.clj)

    self.verdictId = ko.observable();
    self.backendId = ko.observable();
    self.status = ko.observable();
    self.name = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    self.refresh = function(application, verdictId) {
      self.application(ko.mapping.fromJS(application));
      if (application.verdict) {
        self.reset(application.verdict);
      }
    };

    self.reset = function(verdict) {
      self.verdictId(verdict.id);
      self.status(verdict.status);
      self.name(verdict.name);
      self.given(verdict.given);
      self.official(verdict.official);
    };

    self.submit = function() {
      var givenMillis = new Date(self.given()).getTime();
      var officialMillis = new Date(self.official()).getTime();
      ajax
        .command("give-verdict", {id: applicationId, verdictId: self.verdictId(), backendId: self.backendId(), status: self.status(), name: self.name(), given: givenMillis, official: officialMillis})
        .success(function() {
          repository.load(applicationId);
          self.reset({});
          window.location.hash = "!/application/"+applicationId+"/verdict";
          return false;
        })
        .call();
      return false;
    };

    self.disabled = ko.computed(function() {
      return !(self.verdictId() && self.status() && self.name() && self.given() && self.official());
    });
  }

  var verdictModel = new VerdictEditModel();
  var authorizationModel = authorization.create();
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({}, "muut.muu");

  function refresh(application, verdictId) {
    currentApplicationId = application.id;
    currentVerdictId = verdictId;

    authorizationModel.refresh(application);
    verdictModel.refresh(application, verdictId);
    attachmentsModel.refresh(application, {type: "verdict", id: verdictId, urlHash: verdictId});
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
