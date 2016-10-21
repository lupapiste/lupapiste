LUPAPISTE.AttachmentsOperationButtonsModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  self.authModel = lupapisteApp.models.applicationAuthModel;

  var attachments = service.attachments;

  self.newAttachment = function() {
    hub.send( "add-attachment-file", {} );
  };

  function AttachmentTemplatesModel() {
    var templateModel = this;
    templateModel.init = function() {
      templateModel.initDone = true;
      templateModel.selectm = $("#dialog-add-attachment-templates-v2 .attachment-templates").selectm();
      templateModel.selectm
        .allowDuplicates(true)
        .ok(_.ary(service.createAttachmentTemplates, 1))
        .cancel(LUPAPISTE.ModalDialog.close);
      return templateModel;
    };

    templateModel.show = function() {
      if (!templateModel.initDone) {
        templateModel.init();
      }

      var data = _.map(appModel.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc(["attachmentType", groupId, "_group_label"]);
        var typeIds = g[1];
        var attachments = _.map(typeIds, function(typeId) {
          var id = {"type-group": groupId, "type-id": typeId};
          var text = loc(["attachmentType", groupId, typeId]);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      templateModel.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates-v2");
      return templateModel;
    };
  }

  var attachmentTemplatesModel = new AttachmentTemplatesModel();
  self.attachmentTemplatesAdd = function() {
    attachmentTemplatesModel.show();
  };

  self.copyUserAttachments = function() {
    hub.send("show-dialog", {ltitle: "application.attachmentsCopyOwn",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "application.attachmentsCopyOwn.confirmationMessage",
                                               yesFn: service.copyUserAttachments}});
  };

  self.canCopyUserAttachments = function() {
    return self.authModel.ok("copy-user-attachments-to-application");
  };

  self.startStamping = function() {
    hub.send("start-stamping", {application: appModel});
  };

  self.canStamp = function() {
    return self.authModel.ok("stamp-attachments");
  };

  self.signAttachments = function() {
    hub.send("sign-attachments", {application: appModel, attachments: attachments});
  };

  self.canSign = function() {
    return self.authModel.ok("sign-attachments");
  };

  self.markVerdictAttachments = function() {
    hub.send("start-marking-verdict-attachments", {application: appModel});
  };

  self.canMarkVerdictAttachments = function() {
    return self.authModel.ok("set-attachments-as-verdict-attachment");
  };

  self.orderAttachmentPrints = function() {
    hub.send("order-attachment-prints", {application: appModel});
  };

  self.canOrderAttachmentPrints = function() {
    return self.authModel.ok("order-verdict-attachment-prints");
  };

};
