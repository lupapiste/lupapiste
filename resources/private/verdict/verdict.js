(function() {
  "use strict";

  var applicationId = null;

  function VerdictEditModel() {
    var self = this;

    self.application = ko.observable();

    self.statuses = _.range(1,43); // 42 different values in verdict in krysp (verdict.clj)

    self.verdictId = ko.observable();
    self.status = ko.observable();
    self.name = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    self.refresh = function(application) {
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
        .command("give-verdict", {id: applicationId, verdictId: self.verdictId(), status: self.status(), name: self.name(), given: givenMillis, official: officialMillis})
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
  var attachmentsModel = new LUPAPISTE.TargetedAttachmentsModel({type: "verdict"}, "muut.muu");

  repository.loaded(["verdict"], function(application) {
    if (applicationId === application.id) {
      authorizationModel.refresh(application);
      verdictModel.refresh(application);
      attachmentsModel.refresh(application);
    }
  });

  hub.onPageChange("verdict", function(e) {
    applicationId = e.pagePath[0];
    repository.load(applicationId);
  });

  $(function() {
    $("#verdict").applyBindings({
      verdictModel: verdictModel,
      authorization: authorizationModel,
      attachmentsModel: attachmentsModel
    });
  });

})();
