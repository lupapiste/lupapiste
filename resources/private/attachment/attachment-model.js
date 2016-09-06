LUPAPISTE.AttachmentModel = function(attachmentData, authModel) {
  "use strict";
  var self = _.assign(this, attachmentData);
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var DEFAULT_VISIBILITY = _.head(LUPAPISTE.config.attachmentVisibilities);

  var service = lupapisteApp.services.attachmentsService;

  // Attachment data fields that are mapped as observables
  var observableFields = ["notNeeded", "contents", "scale", "size", "type", "op", "groupType"];

  self.authModel = authModel;

  self.processing = ko.observable(false);

  _.forEach(observableFields, function(field) {
    _.set(self, field, ko.observable(_.get(attachmentData, field)));
  });

  self.group = ko.observable(buildGroup(attachmentData));

  self.visibility = ko.observable(buildVisibility(attachmentData));

  self.reset = function(attachmentData) {
    _.forEach(observableFields, function(field) {
      _.get(self, field)(_.get(attachmentData, field));
    });

    self.processing(false);

    self.group(buildGroup(attachmentData));

    self.visibility(buildVisibility(attachmentData));

    return _.assign(self, _.omit(attachmentData, observableFields));
  };

  function buildGroup(data) {
    var group = {groupType: data.groupType, id: _.get(data, "op.id"), name: _.get(data, "op.name")};
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
    self.addEventListener(service.serviceName, event, _.ary(_.partial(service.queryOne, self.id), 0));
  }

  self.disposedSubscribe(self.notNeeded, function(val) {
    self.processing(true);
    service.setNotNeeded(self.id, val, {field: "not-needed"});
  });

  addSelfUpdateListener("not-needed");

  // Helper string to subscribe changes in attachemnt type
  self.typeString = ko.computed(function() {
    return [self.type()["type-group"], self.type()["type-id"]].join(".");
  });

  self.disposedSubscribe(self.typeString, function(val) {
    self.processing(true);
    service.setType(self.id, val, {field: "type"});
  });

  addSelfUpdateListener("type");

  // Helper string to subscribe changes in op and groupType
  self.groupString = self.disposedComputed(function() {
    return _.filter([util.getIn(self.group(), ["groupType"]), util.getIn(self.group(), ["id"])], _.isString).join("-");
  });

  self.disposedSubscribe(self.groupString, function(val) {
    self.processing(true);
    self.op(_.omit(val, "groupType"));
    self.groupType(_.get(val, "groupType"));
    service.setMeta(self.id, {group: !_.isEmpty(val) ? self.group() : null}, {field: "group"});
  });

  addSelfUpdateListener("group");

  //
  // Updates which do not require attachment reload
  //

  self.disposedSubscribe(self.contents, function(val) {
    service.setMeta(self.id, {contents: val}, {field: "contents"});
  });

  self.disposedSubscribe(self.scale, function(val) {
    service.setMeta(self.id, {scale: val}, {field: "scale"});
  });

  self.disposedSubscribe(self.size, function(val) {
    service.setMeta(self.id, {size: val}, {field: "size"});
  });

  self.disposedSubscribe(self.visibility, function(val) {
    service.setVisibility(self.id, val, {field: "visibility"});
  });

  self.addEventListener(service.serviceName, {eventType: "update", attachmentId: self.id}, function(params) {
    if (!params.ok) {
      self.processing(false);
    }
  });

};
