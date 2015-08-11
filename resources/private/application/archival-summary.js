(function() {
  "use strict";

  function GroupModel(groupName, groupDesc, attachments) {
    var self = this;
    self.attachments = ko.observableArray(attachments);
    self.groupName = groupName;
    self.groupDesc = groupDesc;
    // computed name, depending if attachments belongs to operation or not
    self.name = ko.computed( function() {
      if ( loc.hasTerm(["operations", self.groupName]) ) {
        if ( self.groupDesc ) {
          return loc(["operations", self.groupName]) + " - " + self.groupDesc;
        } else {
          return loc(["operations", self.groupName]);
        }
      } else {
        return loc(self.groupName);
      }
    });
  }

  var getPreAttachments = function(attachments) {
    return _.filter(attachments, function(attachment) {
      return !_.contains(LUPAPISTE.config.postVerdictStates, attachment.applicationState());
    });
  };

  var filterByArchiveStatus = function(attachments, keepArchived) {
    return _.filter(attachments, function(attachment) {
      if (!attachment.metadata() || !attachment.metadata()["sailytysaika"] || !attachment.metadata()["sailytysaika"]["arkistointi"]()
        || attachment.metadata()["sailytysaika"]["arkistointi"]() === 'ei') {
        return !keepArchived;
      } else {
        return keepArchived;
      }
    });
  };

  var generalAttachmentsStr = "attachments.general";

  var getGroupList = function(attachments) {
    if (_.isEmpty(attachments)) return [];
    var grouped = _.groupBy(attachments, function(attachment) {
      return _.isObject(attachment.op) && attachment.op.id ? attachment.op.id() : generalAttachmentsStr;
    });
    var mapped = _.map(grouped, function(attachments, group) {
      if (group === generalAttachmentsStr) {
        return new GroupModel(group, null, attachments);
      } else {
        var att = _.first(attachments);
        return new GroupModel(att.op.name(), att.op.description(), attachments);
      }
    });
    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return (_.first(group.attachments())).op.created();
      }
    });
  };

  var addAdditionalFieldsToAttachments = function(attachments) {
    return _.map(attachments, function(attachment) {
      attachment.metadata = ko.observable(attachment.metadata);
      attachment.showMetadataEditor = ko.observable(false);
      attachment.retentionDescription = ko.pureComputed(function() {
        var retention = attachment.metadata() ? attachment.metadata()["sailytysaika"] : null;
        if (retention && retention["arkistointi"]()) {
          var retentionMode = retention["arkistointi"]();
          var additionalDetail = "";
          switch(retentionMode) {
            case "toistaiseksi":
              additionalDetail = ", " + loc("laskentaperuste") + " " + loc(retention["laskentaperuste"]());
              break;
            case "määräajan":
              additionalDetail = ", " + retention["pituus"]() + " " + loc("vuotta");
              break;
          }
          return loc(retentionMode) + additionalDetail.toLowerCase();
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      attachment.personalDataDescription = ko.pureComputed(function() {
        var personalData = attachment.metadata() ? attachment.metadata()['henkilotiedot'] : null;
        if (_.isFunction(personalData)) {
          return loc(personalData());
        } else {
          return loc("<Arvo puuttuu>");
        }
      });
      return attachment;
    });
  };

  var model = function(params) {
    var self = this;
    self.attachments = params.application.attachments;
    var preAttachments = ko.pureComputed(function() {
      var preAttachments = getPreAttachments(self.attachments());
      return addAdditionalFieldsToAttachments(preAttachments);
    });
    var archivedAttachments = ko.pureComputed(function() {
      return filterByArchiveStatus(preAttachments(), true);
    });
    var notArchivedAttachments = ko.pureComputed(function() {
      return filterByArchiveStatus(preAttachments(), false);
    });
    self.archivedDocuments = ko.pureComputed(function() {
      return getGroupList(archivedAttachments());
    });
    self.notArchivedDocuments = ko.pureComputed(function() {
      return getGroupList(notArchivedAttachments());
    });
  };

  ko.components.register("archival-summary", {
    viewModel: model,
    template: {element: "archival-summary-template"}
  });

})();