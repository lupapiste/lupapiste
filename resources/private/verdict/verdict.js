(function() {
  "use strict";

  var applicationId = null;

  function VerdictModel() {
    var self = this;

    self.application = ko.observable();

    self.id = ko.observable();
    self.status = ko.observable();
    self.statuses = ['yes', 'no', 'condition'];
    self.name = ko.observable();
    self.given = ko.observable();
    self.official = ko.observable();

    self.refresh = function(application) {
      self.application(ko.mapping.fromJS(application));
      if (application.verdict) {
        self.id(application.verdict.id);
        self.status(application.verdict.status);
        self.name(application.verdict.name);
        self.given(application.verdict.given);
        self.official(application.verdict.official);
      }
    };

    self.submit = function() {
      console.log("submit");
      ajax
        .command("give-verdict", {id: applicationId})
        .success(function() {
          repository.load(applicationId);
          window.location.hash = "!/application/"+applicationId+"/statement";
          return false;
        })
        .call();
      return false;
    };

    self.disabled = ko.computed(function() {
      return false;
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


  var verdictModel = new VerdictModel();
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
