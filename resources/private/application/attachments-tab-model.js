LUPAPISTE.AttachmentsTabModel = function(signingModel, verdictAttachmentPrintsOrderModel) {
  "use strict";

  var self = this;

  self.authorizationModel = lupapisteApp.models.applicationAuthModel;
  self.appModel = lupapisteApp.models.application;
  self.signingModel = signingModel;
  self.verdictAttachmentPrintsOrderModel = verdictAttachmentPrintsOrderModel;

  self.preAttachmentsByOperation = ko.observableArray();
  self.postAttachmentsByOperation = ko.observableArray();
  self.showHelp = ko.observable(false);
  self.attachmentsOperation = ko.observable();
  self.attachmentsOperations = ko.observable([]);

  self.primaryOperation = ko.observable({});

  self.showPostAttachmentsActions = ko.pureComputed(function() {
    return (!self.appModel.inPostVerdictState() && self.preAttachmentsByOperation().length > 0) ||
           (self.appModel.inPostVerdictState()  && self.postAttachmentsByOperation().length > 0);
  });

  var attachmentsOperationsMapping = {
      "attachmentsAdd": {
        loc: loc("application.attachmentsAdd"),
        clickCommand: function() {
          return self.newAttachment();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("upload-attachment");
        }
      },
      "attachmentsCopyOwn": {
        loc: loc("application.attachmentsCopyOwn"),
        clickCommand: function() {
          return self.copyOwnAttachments();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("copy-user-attachments-to-application");
        }
      },
      "newAttachmentTemplates": {
        loc: loc("application.newAttachmentTemplates"),
        clickCommand: function() {
          return self.attachmentTemplatesModel.show();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("create-attachments");
        }
      },
      "stampAttachments": {
        loc: loc("application.stampAttachments"),
        clickCommand: function() {
          return self.startStamping();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("stamp-attachments") && self.appModel.hasAttachment();
        }
      },
      "markVerdictAttachments": {
        loc: loc("application.markVerdictAttachments"),
        clickCommand: function() {
          return self.startMarkingVerdictAttachments();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("set-attachments-as-verdict-attachment") && self.appModel.hasAttachment();
        }
      },
      "orderVerdictAttachments": {
        loc: loc("verdict.orderAttachmentPrints.button"),
        clickCommand: function() {
          self.verdictAttachmentPrintsOrderModel.openDialog();
        },
        visibleFn: function () {
          return self.authorizationModel.ok("order-verdict-attachment-prints") && self.verdictAttachmentPrintsOrderModel.attachments().length;
        }
      },
      "signAttachments": {
        loc: loc("application.signAttachments"),
        clickCommand: function() {
          return self.signingModel.init(self.appModel);
        },
        visibleFn: function () {
          return self.authorizationModel.ok("sign-attachments") && self.appModel.hasAttachment();
        }
      },
      "downloadAll": {
        loc: loc("download-all"),
        clickCommand: function() {
          window.location = "/api/raw/download-all-attachments?id=" + self.appModel.id() + "&lang=" + loc.getCurrentLanguage();
        },
        visibleFn: function () {
          return self.appModel.hasAttachment();
        }
      }
  };

  function updatedAttachmentsOperations(rawAttachments) {
    var commands = [];
    _.forEach(_.keys(attachmentsOperationsMapping), function(k) {
      var operationData = attachmentsOperationsMapping[k];
      if (!operationData) { error("Invalid attachment operations action: " + k); return [];}
      if (!operationData.visibleFn) { error("No visibility resolving method defined for attachment operations action: " + k); return [];}

      var isOptionVisible = (self.authorizationModel) ? operationData.visibleFn(rawAttachments) : false;
      if (isOptionVisible) {
        commands.push({name: k, loc: operationData.loc});
      }
    });
    return commands;
  }

  self.attachmentsOperation.subscribe(function(opName) {
    if (opName) {
      attachmentsOperationsMapping[opName].clickCommand();
      self.attachmentsOperation(undefined);
    }
  });

  self.toggleHelp = function() {
    self.showHelp(!self.showHelp());
  };

  self.refresh = function() {
    var rawAttachments = lupapisteApp.models.application._js.attachments;
    var preAttachments = attachmentUtils.getPreAttachments(rawAttachments);
    var postAttachments = attachmentUtils.getPostAttachments(rawAttachments);

    self.primaryOperation(lupapisteApp.models.application._js.primaryOperation);

    // pre verdict attachments are not editable after verdict has been given
    var preGroupEditable = lupapisteApp.models.currentUser.isAuthority() || !_.includes(LUPAPISTE.config.postVerdictStates, self.appModel.state());
    var preGrouped = attachmentUtils.getGroupByOperation(preAttachments, preGroupEditable, self.appModel.allowedAttachmentTypes());

    var postGrouped = attachmentUtils.getGroupByOperation(postAttachments, true, self.appModel.allowedAttachmentTypes());

    self.preAttachmentsByOperation(preGrouped);
    self.postAttachmentsByOperation(postGrouped);
    self.attachmentsOperation(undefined);
    self.attachmentsOperations(updatedAttachmentsOperations(rawAttachments));
  };

  self.toggleNeeded = _.debounce(function(attachment) {
    // reload application also when error occurs so that model and db dont get out of sync
    ajax.command("set-attachment-not-needed", {id: self.appModel.id(),
                                               attachmentId: attachment.id,
                                               notNeeded: attachment.notNeeded()})
    .success(function() {
      hub.send("indicator-icon", {style: "positive"});
      self.appModel.lightReload();
    })
    .error(self.appModel.lightReload)
    .processing(self.appModel.processing)
    .call();
    return true;
  }, 500);

  self.newAttachment = function() {
    attachment.initFileUpload({
      applicationId: self.appModel.id(),
      attachmentId: null,
      attachmentType: null,
      typeSelector: true,
      opSelector: self.primaryOperation()["attachment-op-selector"],
      archiveEnabled: self.authorizationModel.ok("permanent-archive-enabled")
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };

  self.copyOwnAttachments = function() {
    var doSendAttachments = function() {
      ajax.command("copy-user-attachments-to-application", {id: self.appModel.id()})
        .success(self.appModel.lightReload)
        .processing(self.appModel.processing)
        .call();
      return false;
    };
    hub.send("show-dialog", {ltitle: "application.attachmentsCopyOwn",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "application.attachmentsCopyOwn.confirmationMessage",
                                               yesFn: doSendAttachments}});
  };

  self.deleteSingleAttachment = function(a) {
    var attId = _.isFunction(a.id) ? a.id() : a.id;
    var versions = ko.unwrap(a.versions);
    var doDelete = function() {
      ajax.command("delete-attachment", {id: self.appModel.id(), attachmentId: attId})
        .success(self.appModel.lightReload)
        .processing(self.appModel.processing)
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachmentFromListing"});
      return false;
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: doDelete}});
  };

  self.startStamping = function() {
    hub.send("start-stamping", {application: self.appModel});
  };

  self.startMarkingVerdictAttachments = function() {
    hub.send("start-marking-verdict-attachments", {application: self.appModel});
  };

  function AttachmentTemplatesModel() {
    var templateModel = this;

    templateModel.ok = function(ids) {
      ajax.command("create-attachments", {id: self.appModel.id(), attachmentTypes: ids})
        .success(function() { repository.load(self.appModel.id()); })
        .complete(LUPAPISTE.ModalDialog.close)
        .call();
    };

    templateModel.init = function() {
      templateModel.selectm = $("#dialog-add-attachment-templates .attachment-templates").selectm();
      templateModel.selectm
        .allowDuplicates(true)
        .ok(templateModel.ok)
        .cancel(LUPAPISTE.ModalDialog.close);
      return templateModel;
    };

    templateModel.show = function() {
      var data = _.map(self.appModel.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc(["attachmentType", groupId, "_group_label"]);
        var attachemntIds = g[1];
        var attachments = _.map(attachemntIds, function(a) {
          var id = {"type-group": groupId, "type-id": a};
          var text = loc(["attachmentType", groupId, a]);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      templateModel.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates");
      return templateModel;
    };
  }
  self.attachmentTemplatesModel = new AttachmentTemplatesModel();

  hub.subscribe("op-description-changed", function(e) {
    var opid = e["op-id"];
    var desc = e["op-desc"];

    _.each(self.appModel.attachments(), function(attachment) {
      if (util.getIn(attachment, ["op", "id"]) === opid && ko.isObservable(attachment.op.description)) {
        attachment.op.description(desc);
      }
    });

    self.refresh(self.appModel);
  });

};
