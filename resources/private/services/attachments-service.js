//
// Provides services for attachments tab.
//
//

LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  ko.options.deferUpdates = true;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";
  self.SCHEDULED_FOR_NOT_NEEDED = "scheduled_for_not_needed";

  function createArrayModel(array) {
    return ko.observable(array);
  }

  self.attachments = ko.observableArray([]);
  self.tagGroups = ko.observableArray([]);

  // array of arrays of filters received from backend along with default values.
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.filters = ko.observableArray([]);

  self.activeFilters = ko.pureComputed(function() {
    return _.filter(self.filtersArray(), function(f) {
      return f.filter() === true;
    });
  });

  self.filteredAttachments = ko.pureComputed(
    function() {
      return applyFilters(self.attachments(), self.activeFilters());});

  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.applicationId = lupapisteApp.models.application.id;

  self.applicationId.subscribe(function() {
    // to avoid retaining old filter states when going to different application
    self.internedObservables = {};
  });

  self.clearData = function() {
    // to avoid showing stale data before ajax queries return, after switching views or applications
    self.attachments([]);
    self.filters([]);
    self.tagGroups([]);
  };

  self.queryAll = function queryAll() {
    var fireQuery = function(commandName, responseJsonKey, dataSetter) {
      if (self.authModel.ok(commandName)) {
        ajax.query(commandName, {"id": self.applicationId()})
          .success(function(data) {
            dataSetter(data[responseJsonKey]);
          })
          .onError("error.unauthorized", notify.ajaxError)
          .call();
      }
    };

    fireQuery("attachments", "attachments", self.setAttachments);
    fireQuery("attachments-tag-groups", "tagGroups", self.setTagGroups);
    fireQuery("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.setAttachments = function(data) {
    self.attachments(_.map(data, createArrayModel));
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
  };

  self.setFilters = function(data) {
    self.filters(data);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };



  self.removeAttachment = function(attachmentId) { // use as reference for new attachment remove dialog, also put track-clicks in
    var versions = ko.unwrap(self.getAttachment(attachmentId)).versions;
    var removeSuccessCallback = function() {
      self.attachments.remove(function(attachment) {
        return attachment().id === attachmentId;
      });
      hub.send("indicator-icon", {style: "positive"});
    };
    var doDelete = function() {
      ajax.command("delete-attachment", {id: self.applicationId(), attachmentId: attachmentId})
        .success(removeSuccessCallback)
        .processing(lupapisteApp.models.application.processing)
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachmentFromListing"});
      return false;
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: doDelete}});
  };

  self.updateAttachment = function(attachmentId, updates) {
    var oldAttachment = self.getAttachment(attachmentId);
    if (oldAttachment) {
      self.getAttachment(attachmentId)(_.merge(oldAttachment(), updates));
    }
  };

  // Approving and rejecting attachments
  self.approveAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.APPROVED});
  };
  self.rejectAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.REJECTED});
  };

  self.setNotNeeded = function(attachmentId, flag ) {
    self.updateAttachment(attachmentId, {notNeeded: !flag ?
                                                    flag :
                                                    self.SCHEDULED_FOR_NOT_NEEDED});
  };

  // When an attachment is set to "not needed" with self.setNotNeeded, it
  // is actually first scheduled for the change. Calling this function
  // will change the state from "scheduled" to "not needed".
  self.changeScheduledNotNeeded = function() {
    var attachmentIds = _(self.attachments.peek()).map(function(attachment) {
      return attachment();
    }).filter({notNeeded: self.SCHEDULED_FOR_NOT_NEEDED}).map("id").value();
    _.forEach(attachmentIds, function(attachmentId) {
      self.updateAttachment(attachmentId, {notNeeded: true});
    });
    self.attachments.valueHasMutated();
  };

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return attachment && attachment.state === self.APPROVED;
  };
  self.isRejected = function(attachment) {
    return attachment && attachment.state === self.REJECTED;
  };
  self.isNotNeeded = function(attachment) {
    return attachment && attachment.notNeeded === true;
  };

  function getUnwrappedAttachmentById(attachmentId) {
    return ko.utils.unwrapObservable(self.getAttachment(attachmentId));
  }

  // returns a function for use in computed
  // If all of the attachments are approved -> approved
  // If some of the attachments are rejected -> rejected
  // Else null
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getUnwrappedAttachmentById),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getUnwrappedAttachmentById),
                      self.isRejected) ? self.REJECTED : null;
      }
    };
  };


  //
  // Filter manipulation
  //

  self.internedObservables = {};

  // keep track of filter toggles, since they are passed over to the UI, and
  // we need to keep using the same ones after tag updates

  function internFilterBoolean(key, def) {
    if (!self.internedObservables[key]) {
      self.internedObservables[key] = ko.observable(def);
    }
    return self.internedObservables[key];
  }

  // filter tag -> observable toggles, shared between service and UI, updated in UI
  self.filtersArray = ko.pureComputed( function() {
    return _.map(_.flatten(self.filters()),
                      function(filter /*, idx // unused */) {
                        return {ltext: "filter." + filter.tag,
                                tag: filter.tag,
                                filter: internFilterBoolean(filter.tag, filter["default"])};});
  });


  self.isAttachmentFiltered = function (att) {
    return _.reduce(self.filters(), function (ok, group) {
          var group_tags = _.map(group, function(x) {return x.tag;});
          var active_tags = _.map(self.activeFilters(), function(x) {return x.tag;});
          var enabled = _.intersection(group_tags, active_tags);
          var tags = att().tags;
          return (ok && (!(_.first(enabled)) || _.first(_.intersection(enabled, tags)))); },  true);
  };

  function applyFilters(attachments /* , active // unused */) {
    return _.filter(attachments, self.isAttachmentFiltered);
  }

  ko.options.deferUpdates = false;
};
