LUPAPISTE.AttachmentModel = function(attachmentData, authModel) {
  "use strict";
  var self = _.assign(this, attachmentData);
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var DEFAULT_VISIBILITY = _.head(LUPAPISTE.config.attachmentVisibilities);

  var service = lupapisteApp.services.attachmentsService;

  var data = attachmentData;

  // Attachment data fields that are mapped as observables
  var observableFields = ["notNeeded", "contents", "scale", "size", "forPrinting", "type", "op", "groupType", "manuallySetConstructionTime"];

  self.authModel = authModel;

  self.processing = ko.observable(false);

  _.forEach(observableFields, function(field) {
    _.set(self, field, ko.observable(_.get(attachmentData, field)));
  });

  self.group = ko.observable(buildGroup(attachmentData));

  self.visibility = ko.observable(buildVisibility(attachmentData));

  self.reset = function(attachmentData) {
    self.disposeAppliedSubscriptions();

    _.forEach(observableFields, function(field) {
      _.get(self, field)(_.get(attachmentData, field));
    });

    _.forEach(_.keys(_.omit(data, observableFields)), _.partial(_.unset, self));

    self.group(buildGroup(attachmentData));

    self.visibility(buildVisibility(attachmentData));

    self.processing(false);

    data = attachmentData;

    self.applySubscriptions();

    return _.assign(self, _.omit(attachmentData, observableFields));
  };

  function buildGroup(data) {
    var group = {groupType: _.get(data, "op.id") ? "operation" : data.groupType,
                 id: _.get(data, "op.id"),
                 name: _.get(data, "op.name")};
    return _.isEmpty(_.filter(group)) ? null : group;
  }

  function buildVisibility(data) {
    return _.has(data.metadata, "nakyvyys") ? data.metadata.nakyvyys : DEFAULT_VISIBILITY;
  }

  //
  // Updates which require attachment model reload
  //

  function addSelfUpdateListener(fieldName) {
    var event = {eventType: "update", ok: true, field: fieldName, attachmentId: self.id};
    self.addEventListener(service.serviceName, event, function() {
      service.queryOne(self.id);
      service.queryTagGroupsAndFilters();
    });
  }

  self.registerApplyableSubscription(self.notNeeded, function(val) {
    self.processing(true);
    service.setNotNeeded(self.id, val, {field: "not-needed"});
  });

  addSelfUpdateListener("not-needed");

  // Helper string to subscribe changes in attachemnt type
  self.typeString = self.disposedComputed(function() {
    return [self.type()["type-group"], self.type()["type-id"]].join(".");
  });

  self.registerApplyableSubscription(self.typeString, function(val) {
    self.processing(true);
    service.setType(self.id, val, {field: "type"});
  });

  addSelfUpdateListener("type");

  // Helper string to subscribe changes in op and groupType
  self.groupString = self.disposedComputed(function() {
    return _.filter([util.getIn(self.group(), ["groupType"]), util.getIn(self.group(), ["id"])], _.isString).join("-");
  });

  self.registerApplyableSubscription(self.groupString, function(val) {
    self.processing(true);
    self.op(_.omit(val, "groupType"));
    self.groupType(_.get(val, "groupType"));
    service.setMeta(self.id, {group: !_.isEmpty(val) ? self.group() : null}, {field: "group"});
  });

  addSelfUpdateListener("group");

  self.registerApplyableSubscription(self.forPrinting, function(val) {
    self.processing(true);
    service.setForPrinting(self.id, val, {field: "forPrinting"});
  });

  addSelfUpdateListener("forPrinting");

  self.registerApplyableSubscription(self.manuallySetConstructionTime, function(val) {
    service.setConstructionTime(self.id, val, {field: "constructionTime"});
  });

  addSelfUpdateListener("constructionTime");

  //
  // Updates which do not require attachment reload
  //

  self.registerApplyableSubscription(self.contents, function(val) {
    service.setMeta(self.id, {contents: val}, {field: "contents"});
  });

  self.registerApplyableSubscription(self.scale, function(val) {
    service.setMeta(self.id, {scale: val}, {field: "scale"});
  });

  self.registerApplyableSubscription(self.size, function(val) {
    service.setMeta(self.id, {size: val}, {field: "size"});
  });

  self.registerApplyableSubscription(self.visibility, function(val) {
    service.setVisibility(self.id, val, {field: "visibility"});
  });

  self.addEventListener(service.serviceName, {eventType: "update", attachmentId: self.id}, function(params) {
    if (!params.ok) {
      self.processing(false);
    }
  });

  self.applySubscriptions();

};
