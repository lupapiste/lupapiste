LUPAPISTE.AttachmentsMultiselectModel = function(params) {
  "use strict";

  var self = this;

  self.params = params;
  self.application = lupapisteApp.models.application;

  function markableAttachment(a) {
    return a.versions && a.versions.length;
  }

  function enhanceAttachment(a) {
    a.selected = ko.observable(a.selected === undefined ? false : a.selected);
    return a;
  }

  // Group sorting differs from attachment page
  function mapAttachmentGroup(group) {
    group.attachments = _(group.attachments).map(enhanceAttachment).value();
    return {
      attachments: group.attachments,
      groupName: group.groupName,
      groupDesc: group.groupDesc,
      name: group.name,
      isGroupSelected: ko.pureComputed(function() {
        return _.every(group.attachments, function(a) {
          return a.selected();
        });
      })
    };
  }

  function getSelectedAttachments(files) {
    return _(files).map("attachments").flatten().filter(function(f) {
      return f.selected();
    }).value();
  }

  function getNonSelectedAttachments(files) {
    return _(files).map("attachments").flatten().filter(function(f) {
      return !f.selected();
    }).value();
  }

  function eachSelected(files) {
    return _(files).map("attachments").flatten().every(function(f) {
      return f.selected();
    });
  }

  self.filteredFiles = _(self.params.attachments).filter(markableAttachment).value();

  // group by post/pre verdict attachments
  var grouped = _.groupBy(self.filteredFiles, function(a) {
    return _.includes(LUPAPISTE.config.postVerdictStates, a.applicationState) ? "post" : "pre";
  });

  // group attachments by operation
  grouped.pre = attachmentUtils.getGroupByOperation(grouped.pre, true, self.application.allowedAttachmentTypes);
  grouped.post = attachmentUtils.getGroupByOperation(grouped.post, true, self.application.allowedAttachmentTypes);

  // map files for marking
  self.preFiles = ko.observableArray(_.map(grouped.pre, mapAttachmentGroup));
  self.postFiles = ko.observableArray(_.map(grouped.post, mapAttachmentGroup));

  self.selectedFiles = ko.pureComputed(function() {
    return getSelectedAttachments(self.preFiles()).concat(getSelectedAttachments(self.postFiles()));
  });

  self.nonSelectedFiles = ko.pureComputed(function() {
    return getNonSelectedAttachments(self.preFiles()).concat(getNonSelectedAttachments(self.postFiles()));
  });

  self.allSelected = ko.pureComputed(function() {
    return eachSelected(self.preFiles()) && eachSelected(self.postFiles());
  });

  self.start = function() {
    params.saveFn(_.map(self.selectedFiles(), "id"), _.map(self.nonSelectedFiles(), "id"));
  };

  self.selectRow = function(row) {
    row.selected(!row.selected());
  };

  function selectAllFiles(value) {
    _(self.preFiles()).map("attachments").flatten().each(function(f) { f.selected(value); });
    _(self.postFiles()).map("attachments").flatten().each(function(f) { f.selected(value); });
  }

  self.selectAll = _.partial(selectAllFiles, true);
  self.selectNone = _.partial(selectAllFiles, false);

  self.toggleGroupSelect = function(group) {
    var sel = group.isGroupSelected();
    _.each(group.attachments, function(a) {
      a.selected(!sel);
    });
  };
};
