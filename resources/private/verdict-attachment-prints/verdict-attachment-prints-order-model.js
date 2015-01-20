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

  function normalizeAttachment(a) {
    var versions = _(a.versions).reverse().value();
    var latestVersion = versions[0];
    return {
      id:           a.id,
      type:         { "type-group": a.type["type-group"], "type-id": a.type["type-id"] },
//      contentType:  latestVersion.contentType,
      contents:     a.contents || loc(['attachmentType', a.type['type-group'], a.type['type-id']]),
      filename:     latestVersion.filename,
//      version:      { major: latestVersion.version.major, minor: latestVersion.version.minor },
//      size:         latestVersion.size,
//      selected:     ko.observable(true)
      orderAmount:  ko.observable("2")
    };
  }

  self.ok = ko.computed(function() {
    var attachmentOrderCountsAreNumbers = _.every(self.attachments(), function(a) {
      return !_.isNaN(_.parseInt(a.orderAmount(), 10));
    }, self);
    return self.authorizationModel.ok("order-verdict-attachment-prints")
           && !self.processing()
           && attachmentOrderCountsAreNumbers
           && !_.isEmpty(self.ordererOrganization())
           && !_.isEmpty(self.ordererEmail())
           && !_.isEmpty(self.ordererPhone())
           && !_.isEmpty(self.applicantName())
           && !_.isEmpty(self.kuntalupatunnus())
           && !_.isEmpty(self.propertyId())
           && !_.isEmpty(self.lupapisteId())
           && !_.isEmpty(self.address());
  });

  // Open the dialog

  self.init = function(bindings) {
    var app = ko.toJS(bindings.application);
    var attachments = _(app.attachments || []).filter(function(a) {return a.forPrinting && a.versions && a.versions.length;}).map(normalizeAttachment).value();

    self.application = app;
    self.processing(false);
    self.pending(false);
    self.errorMessage("");
    self.attachments(attachments);

    self.ordererOrganization(app.organizationName || "");
    self.ordererEmail("kirjaamo@rakennusvalvonta");   // TODO: Lisaa organisaatioille tama tieto, ja paakayttajalle muokkausmahdollisuus
    self.ordererPhone("09 1234 123");                 // TODO: Lisaa organisaatioille tama tieto, ja paakayttajalle muokkausmahdollisuus
    self.applicantName(app.applicant || "");
    self.kuntalupatunnus((app.verdicts && app.verdicts[0] && app.verdicts[0].kuntalupatunnus) ? app.verdicts[0].kuntalupatunnus : "");
    self.propertyId(app.propertyId);
    self.lupapisteId(app.id);
    self.address(app.address);

    self.authorizationModel.refresh(app.id);
  };

  self.openDialog = function(bindings) {
    console.log("verdict-model, openDialog, bindings: ", bindings);
    self.init(bindings);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  }

  self.orderAttachmentPrints = function(/*bindings*/) {
//    console.log("verdict-model, orderAttachmentPrints, bindings: ", bindings);

    var data = {
      id: self.application.id,
      attachments: self.attachments,
      orderInfo: {
        ordererOrganization: self.ordererOrganization,
        ordererEmail: self.ordererEmail,
        ordererPhone: self.ordererPhone,
        applicantName: self.applicantName,
        kuntalupatunnus: self.kuntalupatunnus,
        propertyId: self.propertyId,
        lupapisteId: self.lupapisteId,
        address: self.address
      }
    };

    ajax.command("order-verdict-attachment-prints", data)
      .processing(self.processing)
      .pending(self.pending)
      .success(function(d) {
        var content = loc("verdict-attachment-prints-order.order-dialog.ready", d.verdictPrintCount);
        LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), content);
        pageutil.showAjaxWait();
        repository.load(self.application.id);
      })
      .error(function(d) {
//        LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), loc(d.text));
        self.errorMessage(d.text);
      })
      .call();
  };

};
