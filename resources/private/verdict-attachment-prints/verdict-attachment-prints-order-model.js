LUPAPISTE.VerdictAttachmentPrintsOrderModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.dialogSelector = "#dialog-verdict-attachment-prints-order";

  self.propertyId = lupapisteApp.models.application.propertyId;
  self.applicantName = lupapisteApp.models.application.applicant;
  self.lupapisteId = lupapisteApp.models.application.id;
  self.address = lupapisteApp.models.application.address;
  self.attachmentsService = lupapisteApp.services.attachmentsService;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable();
  self.attachments = self.disposedPureComputed(function() {
    return _(self.attachmentsService.attachments() || [])
      .map(ko.toJS)
      .filter(printableAttachment)
      .map(enrichAttachment)
      .value();
  });

  self.kopiolaitosEmail = ko.observable("");
  self.ordererOrganization = ko.observable("");
  self.ordererAddress = ko.observable("");
  self.ordererEmail = ko.observable("");
  self.ordererPhone = ko.observable("");
  self.kuntalupatunnus = ko.observable("");

  // Last good orderer details for a specific application.
  var ordererInfo = {};

  self.propertyIdHumanReadable = ko.pureComputed({
    read: function(){
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    },
    write: function(value) {
      self.propertyId(util.prop.toDbFormat(value));
    },
    owner: self
  });

  self.badEmail = self.disposedPureComputed( function() {
    var email = _.trim( self.ordererEmail() );
    return !( _.isBlank( email ) || util.isValidEmailAddress( email ));
  });

  self.badPhone = self.disposedPureComputed( function() {
    var phone = _.trim( self.ordererPhone() );
    return !( _.isBlank( phone ) || util.isValidPhoneNumber( phone ));
  });

  self.authorizationModel = lupapisteApp.models.applicationAuthModel;

  self.ok = self.disposedPureComputed(function() {
    var attachmentOrderCountsAreNumbers = _.every(self.attachments(), function(a) {
      return util.isNonNegative(a.orderAmount());
    });
    var mandatoryDialogFieldValues = [self.ordererOrganization(),
                             self.ordererEmail(),
                             self.ordererPhone(),
                             self.applicantName(),
                             self.propertyId(),
                             self.lupapisteId(),
                             self.address()];
    var nonEmptyFields = _.every(mandatoryDialogFieldValues, function(v){ return !_.isEmpty(v); });
    return self.authorizationModel.ok("order-verdict-attachment-prints")
      && !self.processing()
      && !_.isEmpty(self.kopiolaitosEmail())
      && attachmentOrderCountsAreNumbers
      && nonEmptyFields
      && util.isValidEmailAddress( _.trim( self.ordererEmail() ))
      && util.isValidPhoneNumber( _.trim( self.ordererPhone() ));
  });

  self.printsOrderButtonTitle = self.disposedPureComputed(function () {
    return self.attachments().length ? loc("verdict-attachment-prints-order.order-dialog.title") : loc("verdict-attachment-prints-order.verdict-attachments-selected");
  });


  // Helper functions

  function  enrichAttachment(a) {
    a.filename = a.latestVersion.filename;
    a.fileId = a.latestVersion.fileId;
    a.contents = a.contents || loc(["attachmentType", a.type["type-group"], a.type["type-id"]]);
    a.orderAmount = ko.observable("2");
    return a;
  }

  function printableAttachment(a) {
    return a.forPrinting && a.versions && a.versions.length;
  }

  function normalizeAttachments(attachments) {
    return _.map(attachments, function(a) {
      a.amount = parseInt(a.orderAmount(), 10);
      return _.pick(a, ["id", "amount"]);
    });
  }

  function countOrderedAttachments(attachments) {
    return ko.utils.arrayFilter(attachments, function(a) {
      if(a.orderAmount() > 0) {
        return true;}}).length;
  }

  self.refresh = function() {
    var application = lupapisteApp.models.application._js;
    self.processing(false);
    self.pending(false);
    self.errorMessage(null);

    var appId = application.id;

    var kopiolaitosMeta = application.organizationMeta.kopiolaitos;
    var currentUserName = lupapisteApp.models.currentUser.firstName() + " " + lupapisteApp.models.currentUser.lastName();
    var ordererName = (application.organizationMeta.name || "") + ", " + currentUserName;

    self.kopiolaitosEmail(kopiolaitosMeta.kopiolaitosEmail || "");
    if (_.isEmpty(self.kopiolaitosEmail())) {
      self.errorMessage(loc("verdict-attachment-prints-order.order-dialog.no-kopiolaitos-email-set"));
    }
    if( appId !== ordererInfo.id ) {
      ordererInfo = {
        id: appId,
        name: ordererName,
        address: kopiolaitosMeta.kopiolaitosOrdererAddress,
        email: kopiolaitosMeta.kopiolaitosOrdererEmail,
        phone: kopiolaitosMeta.kopiolaitosOrdererPhone
      };
    }

    self.kuntalupatunnus(util.getIn(application, ["kuntalupatunnukset", 0], ""));
  };

  self.openDialog = function() {
    self.ordererOrganization( _.trim( ordererInfo.name ));
    self.ordererAddress( _.trim( ordererInfo.address ));
    self.ordererEmail(_.trim( ordererInfo.email ));
    self.ordererPhone( _.trim( ordererInfo.phone ));
    self.attachmentsService.queryAttachments();
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  // Send the prints order

  self.orderAttachmentPrints = function() {
    ordererInfo = {
      id: ordererInfo.id,
      name: _.trim( self.ordererOrganization() ),
      address: _.trim(self.ordererAddress()),
      email: _.trim(self.ordererEmail()),
      phone: _.trim(self.ordererPhone())
    };

    var data = {
      id: lupapisteApp.models.application.id(),
      lang: loc.getCurrentLanguage(),
      attachmentsWithAmounts: normalizeAttachments(self.attachments()),
      orderInfo: {
        ordererOrganization: ordererInfo.name,
        ordererAddress: ordererInfo.address,
        ordererEmail: ordererInfo.email,
        ordererPhone: ordererInfo.phone,
        applicantName: self.applicantName(),
        kuntalupatunnus: self.kuntalupatunnus(),
        propertyId: self.propertyIdHumanReadable(),
        lupapisteId: self.lupapisteId(),
        address: self.address()
      }
    };

    ajax.command("order-verdict-attachment-prints", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        lupapisteApp.models.application.reload();
        LUPAPISTE.ModalDialog.close();  // close the prints ordering dialog first
        hub.send( "show-dialog",
                  {ltitle: "verdict-attachment-prints-order.order-dialog.title",
                   component: "ok-dialog",
                   size: "small",
                   componentParams: {text: loc("verdict-attachment-prints-order.order-dialog.ready",
                                               countOrderedAttachments(self.attachments()))}});
      })
      .error(function(d) {
        error(d);
        if (d.failedEmails) {
          self.errorMessage(loc(d.text, d.failedEmails));
        } else {
          self.errorMessage(loc(d.text));
        }
      })
      .call();
  };

  self.addHubListener("refresh-verdict-attachments-orders", self.refresh);
  self.addHubListener("order-attachment-prints", self.openDialog);

};
