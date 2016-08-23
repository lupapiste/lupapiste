LUPAPISTE.VerdictAttachmentPrintsOrderHistoryModel = function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-verdict-attachment-prints-order-history";

  self.historyItems = ko.observable([]);

  self.refresh = function() {
    var transfers = lupapisteApp.models.application._js.transfers;
    var items = _(transfers || [])
      .filter(function(item) { return item.type === "verdict-attachment-print-order"; })
      .sortBy(function(item) { return -item.timestamp; })  // descending order
      .value();
    self.historyItems(items);
  };

  // Open the Prints order history dialog
  self.openPrintsOrderHistoryDialog = _.partial(LUPAPISTE.ModalDialog.open, self.dialogSelector);

  var hubId = hub.subscribe("refresh-verdict-attchments-orders", self.refresh);

  self.dispose = _.partial(hub.unsubscribe, hubId);

};
