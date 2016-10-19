LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.pageName = "attachments-listing";

  var legendTemplate = _.template( "<div class='like-btn'>"
                                   + "<i class='<%- icon %>'></i>"
                                   + "<span><%- text %></span>"
                                   + "</div>");
  var legend = [["lupicon-circle-check positive", "ok.title"],
                ["lupicon-circle-star primary", "new.title"],
                ["lupicon-circle-attention negative", "missing.title"],
                ["lupicon-circle-pen positive", "attachment.signed"],
                ["lupicon-circle-arrow-up positive", "application.attachmentSentDate"],
                ["lupicon-circle-stamp positive", "attachment.stamped"],
                ["lupicon-circle-section-sign positive", "attachment.verdict-attachment"],
                ["lupicon-lock primary", "attachment.not-public-attachment"]];

  self.legendHtml = _( legend )
    .map( function( leg ) {
      return legendTemplate( {icon: _.first( leg ),
                              text: loc( _.last( leg ))});
    })
    .value()
    .join("<br>");

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  var filterSet = service.getFilters( self.pageName );

  var attachments = service.attachments;

  var filteredAttachments = self.disposedPureComputed(function() {
    return _.filter(attachments(), filterSet.isFiltered);
  });

  self.authModel = lupapisteApp.models.applicationAuthModel;

  hub.send( "scrollService::follow", {hashRe: /\/attachments$/} );

  function addAttachmentFile( params ) {              // Operation buttons
    var attachmentId = _.get( params, "attachmentId");
    var attachmentType = _.get( params, "attachmentType");
    var attachmentGroup = _.get( params, "attachmentGroup" );
    attachment.initFileUpload({
      applicationId: appModel.id(),
      attachmentId: attachmentId,
      attachmentType: attachmentType,
      typeSelector: !attachmentType,
      group: _.get(attachmentGroup, "groupType"),
      operationId: _.get(attachmentGroup, "id"),
      opSelector: attachmentId ? false : lupapisteApp.models.application.primaryOperation()["attachment-op-selector"](),
      archiveEnabled: self.authModel.ok("permanent-archive-enabled")
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  }

  self.newAttachment = _.ary( addAttachmentFile, 0 ); // Operation buttons

  self.addHubListener( "add-attachment-file", addAttachmentFile );

  // After attachment query
  function afterQuery( params ) {
    var id = _.get( params, "attachmentId");
    if( id && pageutil.lastSubPage() === "attachments" ) {
      pageutil.openPage( "attachment", appModel.id() + "/" + id);
    }
  }
  self.addEventListener( service.serviceName, {eventType: "query", triggerCommand: "upload-attachment"}, afterQuery );

  self.addEventListener(service.serviceName, {eventType: "update", commandName: "approve-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });

  self.addEventListener(service.serviceName, {eventType: "update", commandName: "reject-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });

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
  self.attachmentTemplatesModel = new AttachmentTemplatesModel(); //
  self.attachmentTemplatesAdd = function() {                      // Operation buttons
    self.attachmentTemplatesModel.show();                         //
  };
  self.addEventListener(service.serviceName, "create", LUPAPISTE.ModalDialog.close);

  self.addEventListener(service.serviceName, "remove", util.showSavedIndicator);

  self.addEventListener(service.serviceName, "copy-user-attachments", util.showSavedIndicator);

  self.copyUserAttachments = function() {                    // Operation buttons
    hub.send("show-dialog", {ltitle: "application.attachmentsCopyOwn",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "application.attachmentsCopyOwn.confirmationMessage",
                                               yesFn: service.copyUserAttachments}});
  };

  self.canCopyUserAttachments = function() {                // Operation buttons
    return self.authModel.ok("copy-user-attachments-to-application");
  };

  self.startStamping = function() {                         // Operation buttons
    hub.send("start-stamping", {application: appModel});
  };

  self.canStamp = function() {                              // Operation buttons
    return self.authModel.ok("stamp-attachments");
  };

  self.signAttachments = function() {                       // Operation buttons
    hub.send("sign-attachments", {application: appModel, attachments: attachments});
  };

  self.canSign = function() {                               // Operation buttons
    return self.authModel.ok("sign-attachments");
  };

  self.markVerdictAttachments = function() {                // Operation buttons
    hub.send("start-marking-verdict-attachments", {application: appModel});
  };

  self.canMarkVerdictAttachments = function() {             // Operation buttons
    return self.authModel.ok("set-attachments-as-verdict-attachment");
  };

  self.orderAttachmentPrints = function() {                 // Operation buttons
    hub.send("order-attachment-prints", {application: appModel});
  };

  self.canOrderAttachmentPrints = function() {              // Operation buttons
    return self.authModel.ok("order-verdict-attachment-prints");
  };

  self.hasUnfilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(attachments());
  });

  self.hasFilteredAttachments = self.disposedPureComputed(function() {
    return !_.isEmpty(filteredAttachments());
  });

  self.hasFilters = self.disposedPureComputed(function() {
    return !_.isEmpty(filterSet.filters());
  });

};
