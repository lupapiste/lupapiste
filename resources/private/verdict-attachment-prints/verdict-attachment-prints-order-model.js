LUPAPISTE.VerdictAttachmentPrintsOrderModel = function(/*dialogSelector, confirmSuccess*/) {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-verdict-attachment-prints-order"; //dialogSelector;
//  self.confirmSuccess = confirmSuccess;
  self.application = null;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
  self.errorMessage = ko.observable("");
//  self.password = ko.observable("");
  self.attachments = ko.observable([]);
//  self.selectedAttachments = ko.computed(function() { return _.filter(self.attachments(), function(a) {return a.selected();}); });

  self.ordererOrganization = ko.observable("");
  self.ordererEmail = ko.observable("");
  self.ordererPhone = ko.observable("");
  self.applicantName = ko.observable("");
  self.kuntalupatunnus = ko.observable("");
  self.propertyId = ko.observable("");
  self.lupapisteId = ko.observable("");
  self.address = ko.observable("");

  self.authorizationModel = authorization.create();

  self.ok = ko.computed(function() {
    var attachmentOrderCountsAreNumbers = _.every(self.attachments(), function(a) {
      return !_.isNaN(_.parseInt(a.orderAmount(), 10));
    }, self);
    return self.authorizationModel.ok("order-verdict-attachment-prints") &&
    !self.processing() &&
    attachmentOrderCountsAreNumbers &&
    !_.isEmpty(self.ordererOrganization()) &&
    !_.isEmpty(self.ordererEmail()) &&
    !_.isEmpty(self.ordererPhone()) &&
    !_.isEmpty(self.applicantName()) &&
    !_.isEmpty(self.kuntalupatunnus()) &&
    !_.isEmpty(self.propertyId()) &&
    !_.isEmpty(self.lupapisteId()) &&
    !_.isEmpty(self.address());
  });

  function enrichAttachment(a) {
    var versions = _(a.versions).reverse().value();
    var latestVersion = versions[0];
    a.filename = latestVersion.filename;
    a.contents = a.contents || loc(["attachmentType", a.type["type-group"], a.type["type-id"]])
    a.orderAmount = ko.observable("2");
    return a;
  }

  // Open the dialog

  self.init = function(bindings) {
    self.application = ko.toJS(bindings.application);
    var orgMeta = self.application.organizationMeta;
    var attachments = _(self.application.attachments || [])
                      .filter(function(a) { return a.forPrinting && a.versions && a.versions.length; })
                      .map(enrichAttachment)
                      .value();
    self.attachments(attachments);
    self.processing(false);
    self.pending(false);
    self.errorMessage("");

    self.ordererOrganization(self.application.organizationName || "");
    self.ordererEmail(orgMeta.kopiolaitos.kopiolaitosOrdererEmail || "");
    self.ordererPhone(orgMeta.kopiolaitos.kopiolaitosOrdererPhone || "");
    self.applicantName(self.application.applicant || "");
    self.kuntalupatunnus((self.application.verdicts && self.application.verdicts[0] && self.application.verdicts[0].kuntalupatunnus) ? self.application.verdicts[0].kuntalupatunnus : "");
    self.propertyId(self.application.propertyId);
    self.lupapisteId(self.application.id);
    self.address(self.application.address);

    self.authorizationModel.refresh(self.application.id);
  };

  self.openDialog = function(bindings) {
    self.init(bindings);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

  self.orderAttachmentPrints = function() {
    // cannot replace the orderAmount observable itself, so need to create a new "amount" key
    _.forEach(self.attachments(), function(a) {
      a.amount = ko.unwrap(a.orderAmount);
    });
    var data = {
      id: self.application.id,
      lang: loc.getCurrentLanguage(),
      attachmentsWithAmounts: self.attachments(),
      orderInfo: {
        ordererOrganization: self.ordererOrganization(),
        ordererEmail: self.ordererEmail(),
        ordererPhone: self.ordererPhone(),
        applicantName: self.applicantName(),
        kuntalupatunnus: self.kuntalupatunnus(),
        propertyId: self.propertyId(),
        lupapisteId: self.lupapisteId(),
        address: self.address()
      }
    };

    ajax.command("order-verdict-attachment-prints", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(function() {
        var content = loc("verdict-attachment-prints-order.order-dialog.ready", self.attachments().length);
        LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), content);
        pageutil.showAjaxWait();
        repository.load(self.application.id);
      })
      .error(function(d) {
        error(d);
        self.errorMessage(d.text);
      })
      .call();
  };

};
