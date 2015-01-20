LUPAPISTE.VerdictAttachmentPrintsOrderModel = function(/*dialogSelector, confirmSuccess*/) {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-verdict-attachment-prints-order"; //dialogSelector;
//  self.confirmSuccess = confirmSuccess;
  self.application = null;

  self.processing = ko.observable(false);
  self.pending = ko.observable(false);
//  self.password = ko.observable("");
  self.attachments = ko.observable([]);
//  self.selectedAttachments = ko.computed(function() { return _.filter(self.attachments(), function(a) {return a.selected();}); });
  self.errorMessage = ko.observable("");

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
    return self.authorizationModel.ok('order-verdict-attachment-prints') && !self.processing() && attachmentOrderCountsAreNumbers;
  });

  // Open the dialog

  self.init = function(application) {
    var app = ko.toJS(application);
    var attachments = _(app.attachments || []).filter(function(a) {return a.forPrinting && a.versions && a.versions.length;}).map(normalizeAttachment).value();

    self.application = app;
//    self.password("");
//    self.processing(false);
//    self.pending(false);
    self.errorMessage("");
    self.attachments(attachments);

    self.authorizationModel.refresh(application.id);
  };

  self.openDialog = function(bindings) {
    console.log("verdict-model, openDialog, bindings: ", bindings);
    self.init(bindings.application);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  }

  self.orderAttachmentPrints = function(/*bindings*/) {
//    console.log("verdict-model, orderAttachmentPrints, bindings: ", bindings);
    ajax.command("order-verdict-attachment-prints", {id: self.application.id})
    .processing(self.processing)
    .pending(self.pending)
    .success(function(d) {
      var content = loc("verdict-attachment-prints-order.order-dialog.ready", d.verdictPrintCount);
      LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), content);
      pageutil.showAjaxWait();
      repository.load(self.application.id);
    })
    .error(function(d) {
//      LUPAPISTE.ModalDialog.showDynamicOk(loc("verdict-attachment-prints-order.order-dialog.title"), loc(d.text));
      self.errorMessage(d.text);
    })
    .call();
  };


//  self.sign = function() {
//    self.errorMessage("");
//    var id = self.application.id;
//    var attachmentIds = _.map(self.selectedAttachments(), "id");
//    if (attachmentIds && attachmentIds.length) {
//      ajax.command("sign-attachments", {id: id, attachmentIds: attachmentIds, password: self.password()})
//        .processing(self.processing)
//        .pending(self.pending)
//        .success(function() {
//          self.password("");
//          repository.load(id);
//          LUPAPISTE.ModalDialog.close();
//          if (self.confirmSuccess) {
//            LUPAPISTE.ModalDialog.showDynamicOk(loc("application.signAttachments"), loc("signAttachment.ok"));
//          }
//        })
//        .error(function(e) {self.errorMessage(e.text);})
//        .call();
//    }
//  };

};
