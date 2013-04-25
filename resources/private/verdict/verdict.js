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
        self.verdictId(application.verdict.id);
        self.status(application.verdict.status);
        self.name(application.verdict.name);
        self.given(application.verdict.given);
        self.official(application.verdict.official);
      }
    };

    self.submit = function() {
      var givenMillis = new Date(self.given()).getTime();
      var officialMillis = new Date(self.official()).getTime();
      ajax
        .command("give-verdict", {id: applicationId, verdictId: self.verdictId(), status: self.status(), name: self.name(), given: givenMillis, official: officialMillis})
        .success(function() {
          repository.load(applicationId);
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

  function AttachmentsModel(attachmentTarget) {
    var self = this;

    self.attachments = ko.observableArray([]);

    self.refresh = function(application) {
      self.attachments(_.filter(application.attachments,function(attachment) {
        console.log(attachment.target);
        return _.isEqual(attachment.target, attachmentTarget);
      }));
    };

    self.newAttachment = function() {
      attachment.initFileUpload(applicationId, null, "muut.muu", false, attachmentTarget);
    };
  }

  var verdictModel = new VerdictEditModel();
  var authorizationModel = authorization.create();
  var attachmentsModel = new AttachmentsModel({type: "verdict"});

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
