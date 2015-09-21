var attachmentUtils = (function() {
  "use strict";

  var generalAttachmentsStr = "attachments.general";

  function GroupModel(groupName, groupDesc, attachments, editable) {
    var self = this;
    self.attachments = attachments;
    self.groupName = groupName;
    self.groupDesc = groupDesc;
    self.editable = editable;
    // computed name, depending if attachments belongs to operation or not
    self.name = ko.computed( function() {
      if ( loc.hasTerm(["operations", self.groupName]) ) {
        if ( self.groupDesc ) {
          return loc(["operations", self.groupName]) + " - " + self.groupDesc;
        } else {
          return loc(["operations", self.groupName]);
        }
      } else {
        return loc(self.groupName); // "attachments.general"
      }
    });
  }

  var fGroupByOperation = function(attachment) {
    return attachment.op ? attachment.op.id : generalAttachmentsStr;
  };

  /* Sorting function to sort attachments into
   * same order as in allowedAttachmentTypes -observable
   */
  var fSortByAllowedAttachmentType = function(allowedAttachmentTypes, a, b) {
    var types = _.flatten(allowedAttachmentTypes, true);

    var atg = a.type["type-group"];
    var atgIdx = _.indexOf(types, atg);
    var atid = a.type["type-id"];

    var btg = b.type["type-group"];
    var btgIdx = _.indexOf(types, btg);
    var btid = b.type["type-id"];

    if ( atg === btg ) {
      // flattened array of allowed attachment types.
      // types[atgIdx + 1] is array of type-ids,
      // which correnspond to type-group in atgIdx
      return _.indexOf(types[atgIdx + 1], atid) - _.indexOf(types[btgIdx + 1], btid);
    } else {
      return atgIdx - btgIdx;
    }
  };

  /*
   * Returns attachments (source) as GroupModel instances, grouped by grouping function f.
   * Optionally sorts using sort.
   */
  function getAttachmentsByGroup(source, f, sort, editable, allowedAttachmentTypes) {
    var attachments = _.map(source, function(a) {
      a.latestVersion = _.last(a.versions || []);
      a.statusName = LUPAPISTE.statuses[a.state] || "unknown";
      a.required = a.required || false;
      a.authorized = (a.required || a.requestedByAuthority) ? lupapisteApp.models.currentUser.role() === "authority" : true;
      a.notNeeded = ko.observable(a.notNeeded || false);
      a.notNeededFieldDisabled = ko.observable(a.requestedByAuthority ? lupapisteApp.models.currentUser.role() !== "authority" : false);
      return a;
    });
    if ( _.isFunction(sort) ) {
      var sorter = _.partial(sort, allowedAttachmentTypes);
      attachments.sort(sorter);
    }
    var grouped = _.groupBy(attachments, f);
    var mapped = _.map(grouped, function(attachments, group) {
      if ( group === generalAttachmentsStr ) {
        return new GroupModel(group, null, attachments, editable); // group = attachments.general
      } else { // group == op.id
        var att = _.first(attachments);
        return new GroupModel(att.op.name, att.op.description, attachments, editable);
      }
    });
    return _.sortBy(mapped, function(group) { // attachments.general on top, else sort by op.created
      if ( group.groupName === generalAttachmentsStr ) {
        return -1;
      } else {
        return (_.first(group.attachments)).op.created;
      }
    });
  }

  function getPreAttachments(source) {
    return _.filter(source, function(attachment) {
          return !_.contains(LUPAPISTE.config.postVerdictStates, attachment.applicationState);
      });
  }

  function getPostAttachments(source) {
    return _.filter(source, function(attachment) {
          return _.contains(LUPAPISTE.config.postVerdictStates, attachment.applicationState);
      });
  }


  function getGroupByOperation(source, editable, allowedAttachmentTypes) {
    return getAttachmentsByGroup(source, fGroupByOperation, fSortByAllowedAttachmentType, editable, allowedAttachmentTypes);
  }

  function sortAttachmentTypes(attachmentTypes) {
    return _.map(attachmentTypes, function(v) {
      v[1] = v[1].sort(function(a, b) {
        // Sort other last
        if (b === "muu") {
          return -1;
        } else if (a === "muu") {
          return 1;
        }
        return loc(["attachmentType", v[0], a]).localeCompare(loc(["attachmentType", v[0], b]));
      });
      return v;
    });
  }

  return {
    getPreAttachments: getPreAttachments,
    getPostAttachments: getPostAttachments,
    getGroupByOperation: getGroupByOperation,
    sortAttachmentTypes: sortAttachmentTypes
  };

})();
