LUPAPISTE.ApplicationActionsModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  function actions() {
    var app = lupapisteApp.models.application;
    var authOk = lupapisteApp.models.applicationAuthModel.ok;
    var hasInvites = app.hasInvites();
    var noInvites = !hasInvites;
    var idle = !app.processing();
    var stateSame = !app.stateChanged();

    function showIntegrationMessages() {
      hub.send( "show-dialog", {
        ltitle: "application.integration-messages.title",
        size: "medium",
        component: "integration-message-monitor",
        componentParams: {id: app.id()}
      });
    }

    function sendAttachments() {
      pageutil.openPage( "send-attachments", app.id() );
    }

    return app.infoRequest()
      ? [// Inforequest actions
        {
          click: app.convertToApplication,
          visible: authOk( "convert-to-application" ),
          testId: "inforequest-convert-to-application",
          buttonClass: "positive",
          icon: "download",
          ltext: "inforequest.convertToApplication"
        },
        {
          click: app.cancelInforequest,
          visible: authOk( "cancel-inforequest"),
          testId: "inforequest-cancel-btn",
          icon: "circle-remove",
          ltext: "inforequest.cancelInforequest"
        },
        {
          click: app.findOwners,
          visible: authOk( "application-property-owners" ),
          enable: idle,
          testId: "inforequest-property-owners-btn",
          icon: "search",
          ltext: "application.find-owners"
        },
        {
          click: app.resetIndicators,
          visible: authOk( "mark-everything-seen"),
          enable: idle,
          testId: "application-mark-everything-seen-btn",
          icon: "eye",
          ltext: "application.reset-indicators"
        }
      ]
    : [// Application actions
      // Personal invites
      {
        click: _.wrap( undefined, app.approveInvite ),
        visible: app.hasPersonalInvites(),
        enable: idle && authOk( "approve-invite" ),
        buttonClass: "positive",
        testId: "accept-invite-button",
        ltext: "applications.approveInvite"
      },
      {
        click: app.declineInvite,
        visible: app.hasPersonalInvites(),
        enable: idle && authOk( "decline-invitation" ),
        testId: "decline-invite-button",
        ltext: "applications.declineInvite"
      },
      // Company invites
      {
        click: _.wrap( "company", app.approveInvite ),
        visible: app.hasCompanyInvites(),
        enable: idle && authOk( "approve-invite" ),
        buttonClass: "positive",
        testId: "accept-invite-button",
        ltext: "applications.approveCompanyInvite"
      },
      {
        click: app.declineInvite,
        visible: app.hasCompanyInvites(),
        enable: idle && authOk( "decline-invitation" ),
        testId: "decline-invite-button",
        ltext: "applications.declineCompanyInvite"
      },
      {
        click: app.approveExtension,
        visible: noInvites && authOk( "approve-application") && authOk( "approve-ya-extension"),
        enable: stateSame && idle,
        ltitle: "tooltip.approveApplication",
        testId: "approve-extension",
        buttonClass: "positive caps",
        icon: "circle-check",
        ltext: "application.extension.approve"
      },
      {
        click: app.removeBuildings,
        visible: app.isArchivingProject(),
        enable: authOk( "remove-buildings" ),
        testId: "remove-buildings-button",
        icon: "circle-remove",
        ltext: "application.remove.buildings"
      },
      {
        click: app.propertyFormationApp,
        visible: noInvites && authOk( "create-property-formation-app" ),
        enable: idle,
        testId: "application-create-property-formation-app-btn",
        icon: "arrow-right",
        ltext: "application.createPropertyFormationApp",
      },
      {
        click: app.createDiggingPermit,
        visible: authOk( "create-digging-permit" ),
        enable: idle,
        testId: "create-digging-permit-button",
        icon: "circle-plus",
        ltext: "application.createDiggingPermit"
      },
      {
        click: app.toApproveApplicationAfterVerdict,
        visible: noInvites && authOk("approve-application-after-verdict"),
        enable: stateSame && idle,
        testId: "approve-application-after-verdict",
        icon: "circle-arrow-right",
        ltext: "application.moveToBackendAfterVerdict"
      },
      {
        click: app.toAsianhallinta,
        visible: noInvites && authOk("application-to-asianhallinta"),
        enable: idle,
        ltitle: "tooltip.toAsianhallinta",
        testId: "to-asianhallinta",
        icon: "circle-arrow-right",
        buttonClass: "positive caps",
        ltext: "application.toAsianhallinta"
      },
      {
        click: app.requestForComplement,
        visible: noInvites && authOk( "request-for-complement" ),
        enable: stateSame && idle,
        testId: "request-for-complement",
        icon: "circle-arrow-left",
        ltext: "application.requestForComplement"
      },
      {
        click: app.resetIndicators,
        visible: noInvites && authOk( "mark-everything-seen" ),
        enable: idle,
        testId: "application-mark-everything-seen-btn",
        icon: "eye",
        ltext: "application.reset-indicators"
      },
      {
        click: app.gotoLinkPermitCard,
        visible: noInvites && authOk( "add-link-permit" ),
        enable: idle,
        testId: "application-add-link-permit-btn",
        icon: "circle-plus",
        ltext: app.permitType() === "KT"
          ? "application.kt.addLinkPermit"
          : "application.addLinkPermit"
      },
      {
        click: app.addOperation,
        visible: noInvites && authOk( "add-operation" ),
        enable: idle,
        testId: "add-operation",
        icon: "circle-plus",
        ltext: "application.addOperation"
      },
      {
        click: app.createChangePermit,
        visible: noInvites && authOk( "create-change-permit" ),
        enable: idle,
        testId: "change-permit-create-btn",
        icon: "circle-plus",
        ltext: "application.createChangePermit"
      },
      {
        click: app.createEncumbrancePermit,
        visible: noInvites && authOk( "create-encumbrance-permit" ),
        enable: idle,
        testId: "encumbrance-create-btn",
        icon: "circle-plus",
        ltext: "application.createEncumbrancePermit"
      },
      {
        click: app.createContinuationPeriodPermit,
        visible: noInvites && authOk( "create-continuation-period-permit" ),
        enable: idle,
        testId: "continuation-period-create-btn",
        icon: "clock",
        ltext: "application.createContinuationPeriodPermit"
      },
      // The following two definitions are export-attachments component
      // written explicitly. The component is still used in the
      // attachments tab.
      {
        click:  sendAttachments,
        visible: noInvites && authOk( "move-attachments-to-backing-system" ),
        testId: "export-attachments-to-backing-system",
        buttonClass: "positive",
        icon: "circle-arrow-right",
        ltext: "application.exportAttachments"
      },
      {
        click: sendAttachments,
        visible: noInvites && authOk( "attachments-to-asianhallinta" ),
        testId: "export-attachments-to-asianhallinta",
        buttonClass: "positive",
        icon: "circle-arrow-right",
        ltext: "application.exportAttachments"
      },
      {
        click: app.toBackingSystem,
        visible: noInvites
          && authOk( "redirect-to-vendor-backend")
        // Not in a frame
          && (!window.parent || window.parent === window),
        icon: "circle-arrow-right",
        ltext: "application.showInBackingSystem"
      },
      {
        click: _.wrap( app, app.externalApi.openApplication ),
        visible: noInvites
          && app.externalApi.enabled()
          && app.externalApi.ok( "openPermit" ),
        testId: "external-open-permit",
        icon: "circle-arrow-right",
        ltext: "application.showInBackingSystem"
      },
      {
        click: _.wrap( app, app.externalApi.showOnMap ),
        visible: noInvites
          && app.externalApi.enabled()
          && app.externalApi.ok( "showPermitOnMap" ),
        testId: "external-show-on-map",
        icon: "location",
        ltext: "a11y.action.show-on-map"
      },
      {
        click: showIntegrationMessages,
        visible: noInvites && authOk( "integration-messages" ),
        enable: idle,
        testId: "show-integration-messages",
        icon: "upload",
        ltext: "application.integration-messages.open"
      },
      {
        click: app.copy,
        visible: app.canBeCopied(),
        enable: idle && authOk( "copy-application" ),
        testId: "copy-application-button",
        icon: "copy",
        ltext: "application.copy"
      },
      {
        click: app.findOwners,
        visible: noInvites && authOk( "application-property-owners" ),
        enable: idle,
        testId: "application-property-owners-btn",
        icon: "search",
        ltext: "application.find-owners"
      },
      {
        click: app.refreshKTJ,
        visible: noInvites && authOk( "refresh-ktj" ),
        enable: idle,
        testId: "application-refresh-ktj-btn",
        icon: "refresh",
        ltext: "application.refreshKTJ"
      },
      {
        click: app.addProperty,
        visible: noInvites && app.showAddPropertyButton(),
        enable: idle,
        testId: "add-property",
        icon: "circle-plus",
        ltext: "secondary-kiinteistot._append_label"
      },
      {
        click: app.exportPdf,
        visible: noInvites && authOk( "pdf-export" ),
        enable: idle,
        testId: "application-pdf-btn",
        icon: "print",
        ltext: "application.pdf"
      },
      {
        click: app.cancelApplication,
        visible: noInvites && authOk( "cancel-application" ),
        enable: stateSame && idle,
        testId: "application-cancel-btn",
        icon: "circle-remove",
        ltext: app.isArchivingProject()
          ? "application.cancelArchivingProject"
          : "application.cancelApplication"
      },
      {
        click: app.undoCancellation,
        visible: noInvites && authOk( "undo-cancellation" ),
        enable: stateSame && idle,
        ltitle: "tooltip.undoCancellation",
        testId: "application-undo-cancellation-btn",
        icon: "circle-attention",
        ltext: "application.undoCancellation"
      }
    ];
  }

  self.buttonDefinitions = self.disposedPureComputed( function() {
    return _( actions() )
      .filter( "visible" )
      .map( function( obj ) {
        var buttonDef =  _.pick( obj, ["click", "enable", "testId",
                                       "icon", "ltext", "buttonClass"]);
        if( obj.ltitle ) {
          _.set( buttonDef, "attr.title", loc( obj.ltitle ));
        }
        return buttonDef;
      })
      .value();
  });
};
