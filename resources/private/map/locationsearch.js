var locationSearch = (function() {
  "use strict";

  var searchPointByAddress = function(requestContext, address, onSuccess, onFail, processing) {
    return ajax
      .get("/proxy/get-address")
      .param("query", address)
      .param("lang", loc.getCurrentLanguage())
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchLocationByPropertyId = function(requestContext, propertyId, onSuccess, onFail, processing) {
    return ajax
      .get("/proxy/location-by-property-id")
      .param("property-id", util.prop.toDbFormat(propertyId))
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
  };

  var searchPropertyId = function(requestContext, x, y, onSuccess, onFail, processing) {
    if (x > 0 && y > 0 ) {
      return ajax
        .get("/proxy/property-id-by-point")
        .param("x", x).param("y", y)
        .processing(processing || _.noop)
        .success(requestContext.onResponse(onSuccess))
        .fail(requestContext.onResponse(onFail))
        .call();
    }
  };
  // returns also municipality data, compared to searchPropertyId
  var searchPropertyInfo = function(requestContext, x, y, onSuccess, onFail, processing) {
    if (x > 0 && y > 0 ) {
      return ajax
        .get("/proxy/property-info-by-point")
        .param("x", x).param("y", y)
        .processing(processing || _.noop)
        .success(requestContext.onResponse(onSuccess))
        .fail(requestContext.onResponse(onFail))
        .call();
    }
  };
  var searchPropertyIdByWKT = function(requestContext, wkt, radius, onSuccess, onFail, processing) {
    var r = _.isNumber(radius) ? Math.round(radius) : "";
    if (wkt) {
      return ajax
        .get("/proxy/property-info-by-wkt")
        .param("wkt", wkt).param("radius", r)
        .processing(processing || _.noop)
        .success(requestContext.onResponse(onSuccess))
        .fail(requestContext.onResponse(onFail))
        .call();
    }
  };
  var searchAddress = function(requestContext, x, y, onSuccess, onFail, processing) {
    if (x > 0 && y > 0) {
      return ajax
        .get("/proxy/address-by-point")
        .param("x", x)
        .param("y", y)
        .param("lang", loc.getCurrentLanguage())
        .processing(processing || _.noop)
        .success(requestContext.onResponse(onSuccess))
        .fail(requestContext.onResponse(onFail))
        .call();
    }
  };

  var searchOwnersByPropertyIds = function(requestContext, propertyIds, onSuccess, onFail, processing) {
    return ajax.datatables("owners", {propertyIds: propertyIds})
      .processing(processing || _.noop)
      .success(requestContext.onResponse(onSuccess))
      .error(requestContext.onResponse(onFail))
      .call();
  };

  return {
    pointByAddress: searchPointByAddress,
    locationByPropertyId: searchLocationByPropertyId,
    propertyIdByPoint: searchPropertyId,
    propertyInfoByPoint: searchPropertyInfo,
    propertyIdsByWKT: searchPropertyIdByWKT,
    addressByPoint: searchAddress,
    ownersByPropertyIds: searchOwnersByPropertyIds
  };
})();
