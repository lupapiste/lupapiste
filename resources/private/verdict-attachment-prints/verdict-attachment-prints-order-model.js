LUPAPISTE.VerdictAttachmentPrintsOrderModel = function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-verdict-attachment-prints-order";
  self.application = null;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable();
  self.attachments = ko.observable([]);

  self.kopiolaitosEmail = ko.observable("");
  self.ordererOrganization = ko.observable("");
  self.ordererAddress = ko.observable("");
  self.ordererEmail = ko.observable("");
  self.ordererPhone = ko.observable("");
  self.applicantName = ko.observable("");
  self.kuntalupatunnus = ko.observable("");
  self.propertyId = ko.observable("");
  self.propertyIdHumanReadable = ko.pureComputed({
    read: function(){
      return self.propertyId() ? util.prop.toHumanFormat(self.propertyId()) : "";
    },
    write: function(value) {
      self.propertyId(util.prop.toDbFormat(value));
    },
    owner: self
  });
  self.lupapisteId = ko.observable("");
  self.address = ko.observable("");

  self.authorizationModel = lupapisteApp.models.applicationAuthModel;

  self.ok = ko.computed(function() {
    var attachmentOrderCountsAreNumbers = _.every(self.attachments(), function(a) {
      return util.isNonNegative(a.orderAmount());
    });
    var dialogFieldValues = [self.ordererOrganization(),
                             self.ordererEmail(),
                             self.ordererPhone(),
                             self.applicantName(),
                             self.kuntalupatunnus(),
                             self.propertyId(),
                             self.lupapisteId(),
                             self.address()];
    var nonEmptyFields = _.every(dialogFieldValues, function(v){ return !_.isEmpty(v); });
    return self.authorizationModel.ok("order-verdict-attachment-prints") &&
           !self.processing() &&
           !_.isEmpty(self.kopiolaitosEmail()) &&
           attachmentOrderCountsAreNumbers &&
           nonEmptyFields &&
           util.isValidEmailAddress(self.ordererEmail());
  });

  self.printsOrderButtonTitle = ko.computed(function () {
    return self.attachments().length ? loc("verdict-attachment-prints-order.order-dialog.title") : loc("verdict-attachment-prints-order.verdict-attachments-selected");
  });


  // Helper functions

  var enrichAttachment = function(a) {
    a.filename = a.latestVersion.filename;
    a.fileId = a.latestVersion.fileId;
    a.contents = a.contents || loc(["attachmentType", a.type["type-group"], a.type["type-id"]]);
    a.orderAmount = ko.observable("2");
    return a;
  };

  var printableAttachment = function(a) {
    return a.forPrinting && a.versions && a.versions.length;
  };

  var normalizeAttachments = function(attachments) {
    return _.map(attachments, function(a) {
      a.amount = a.orderAmount();
      return _.pick(a, ["id", "forPrinting", "amount", "contents", "type", "fileId", "filename", "versions"]);
    });
  };

  // Open the dialog

  self.refresh = function(applicationModel) {
    self.application = ko.toJS(applicationModel);
    self.processing(false);
    self.pending(false);
    self.errorMessage(null);

    var attachments = _(self.application.attachments || [])
                      .filter(printableAttachment)
                      .map(enrichAttachment)
                      .value();
    self.attachments(attachments);

    var kopiolaitosMeta = ko.unwrap(self.application.organizationMeta).kopiolaitos;
    var currentUserName = lupapisteApp.models.currentUser.firstName() + " " + lupapisteApp.models.currentUser.lastName();
    var ordererName = (self.application.organizationName || "") + ", " + currentUserName;

    self.kopiolaitosEmail(kopiolaitosMeta.kopiolaitosEmail || "");
    if (_.isEmpty(self.kopiolaitosEmail())) {
      self.errorMessage(loc("verdict-attachment-prints-order.order-dialog.no-kopiolaitos-email-set"));
    }

    self.ordererOrganization(ordererName);
    self.ordererAddress(kopiolaitosMeta.kopiolaitosOrdererAddress || "");
    self.ordererEmail(kopiolaitosMeta.kopiolaitosOrdererEmail || "");
    self.ordererPhone(kopiolaitosMeta.kopiolaitosOrdererPhone || "");
    self.applicantName(self.application.applicant || "");
    self.kuntalupatunnus((self.application.verdicts && self.application.verdicts[0] && self.application.verdicts[0].kuntalupatunnus) ? self.application.verdicts[0].kuntalupatunnus : "");
    self.propertyId(self.application.propertyId);
    self.lupapisteId(self.application.id);
    self.address(self.application.address);

  };

  self.openDialog = function(bindings) {
    self.refresh(bindings.application);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  // Send the prints order


  self.orderAttachmentPrints = function() {
    var data = {
      id: self.application.id,
      lang: loc.getCurrentLanguage(),
      attachmentsWithAmounts: normalizeAttachments(self.attachments()),
      orderInfo: {
        ordererOrganization: self.ordererOrganization(),
        ordererAddress: self.ordererAddress(),
        ordererEmail: self.ordererEmail(),
        ordererPhone: self.ordererPhone(),
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
        LUPAPISTE.ModalDialog.close();  // close the prints ordering dialog first
        var content = loc("verdict-attachment-prints-order.order-dialog.ready", self.attachments().length);
        LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), content);
        pageutil.showAjaxWait();
        repository.load(self.application.id);
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

};
