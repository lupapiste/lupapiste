var verdictPageController = (function() {
  "use strict";

  var currentApplicationId = null;
  var currentVerdictId = null;

  function VerdictEditModel() {
    var self = this;

    self.application = ko.observable();

    self.statuses = _.range(1,43); // 42 different values in verdict in krysp (verdict.clj)

    self.backendId = ko.observable();
    self.status = ko.observable();
    self.name = ko.observable();
    self.text = ko.observable();
    self.agreement = ko.observable(false);
    self.section = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    // TODO rewrite
    self.reset = function(verdict) {
      self.backendId(verdict.backendId);
      self.status(verdict.status);
      self.name(verdict.name);
      self.given(verdict.given);
      self.official(verdict.official);
      self.text(verdict.text);
      self.agreement(verdict.agreement);
      self.section(verdict.section);
    };

    self.refresh = function(application, verdictId) {
      self.application(ko.mapping.fromJS(application));

      // TODO rewrite: currently never true
      if (application.verdict) {
        self.reset(application.verdict);
      }
    };

    self.submit = function() {
      var givenMillis = new Date(self.given()).getTime();
      var officialMillis = new Date(self.official()).getTime();
      ajax
        .command("save-verdict-draft",
                 {id: currentApplicationId, verdictId: currentVerdictId,
                  backendId: self.backendId(), status: self.status(),
                  name: self.name(), text: self.text(),
                  section: self.section(),
                  agreement: self.agreement(),
                  given: givenMillis, official: officialMillis})
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
      return !(self.backendId() && self.status() && self.name() && self.given() && self.official());
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
