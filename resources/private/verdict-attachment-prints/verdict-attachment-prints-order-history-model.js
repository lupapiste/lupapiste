LUPAPISTE.VerdictAttachmentPrintsOrderHistoryModel = function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-verdict-attachment-prints-order-history";
  self.application = null;

  self.historyItems = ko.observable([]);

  self.refresh = function(applicationModel) {
    self.application = ko.toJS(applicationModel);
    var items = _(self.application.transfers || [])
      .filter(function(item) { return item.type === "verdict-attachment-print-order"; })
      .sortBy(function(item) { return -item.timestamp; })  // descending order
      .value();
    self.historyItems(items);

  };

  // Open the Prints order history dialog

  self.openPrintsOrderHistoryDialog = function(bindings) {
    self.refresh(bindings.application);
    LUPAPISTE.ModalDialog.open(self.dialogSelector);
  };

};
