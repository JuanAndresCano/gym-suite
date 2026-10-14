# Pruebas de extremo a extremo del API Gateway (http://localhost:8085).
# Requiere Keycloak, los servicios y el gateway corriendo. Uso:  powershell -File scripts\test-gateway.ps1
#
# -Degradado : corre SOLO el caso de degradacion del resumen (clases null + advertencias). Detenga antes
#              clases-service; el resto del script necesita que este arriba.
param([switch]$Degradado)

$ErrorActionPreference = 'Stop'

$kc = 'http://localhost:8080'
$gw = 'http://localhost:8085'

# El password grant esta habilitado en los clients miembros-service, clases-service, entrenadores-service
# y equipos-service del realm "gimnasio" (secret = "<client>-secret"). Se usa miembros-service.
$client = 'miembros-service'

function Get-Token($client,$user,$pass){(Invoke-RestMethod "$kc/realms/gimnasio/protocol/openid-connect/token" -Method Post -Body @{grant_type='password';client_id=$client;client_secret="$client-secret";username=$user;password=$pass}).access_token}
function Api($uri,$method,$token,$body){Invoke-RestMethod -Uri $uri -Method $method -Headers @{Authorization="Bearer $token"} -ContentType 'application/json' -Body $body}

# Los tokens duran 300 s: se pide uno nuevo justo antes de cada uso.
function Token($rol) {
    switch ($rol) {
        'admin'   { Get-Token $client 'admin.test'   'Admin123!' }
        'member'  { Get-Token $client 'member.test'  'Member123!' }
        'trainer' { Get-Token $client 'trainer.test' 'Trainer123!' }
        default   { $null }
    }
}

# Llama al gateway y devuelve el status HTTP y el cuerpo (tambien cuando es un error 4xx/5xx).
function Call($path, $method, $rol, $body) {
    $headers = @{}
    $token = Token $rol
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    try {
        $r = Invoke-WebRequest -Uri "$gw$path" -Method $method -Headers $headers -ContentType 'application/json' -Body $body -UseBasicParsing
        [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        if (-not $_.Exception.Response) { throw }
        [pscustomobject]@{ Status = [int]$_.Exception.Response.StatusCode; Body = $_.ErrorDetails.Message }
    }
}

$script:ok = 0
$script:fallos = 0
function Check($nombre, $esperado, $real, $detalle) {
    if ($esperado -contains $real) {
        $script:ok++
        Write-Host ("[OK]    {0} -> {1}" -f $nombre, $real) -ForegroundColor Green
    } else {
        $script:fallos++
        Write-Host ("[FALLO] {0} -> esperado {1}, obtenido {2} {3}" -f $nombre, ($esperado -join '/'), $real, $detalle) -ForegroundColor Red
    }
}
function Expect($nombre, $esperado, $path, $method, $rol, $body) {
    $r = Call $path $method $rol $body
    Check $nombre $esperado $r.Status $r.Body
    $r
}

$manana = (Get-Date).AddDays(2).ToString("yyyy-MM-dd'T'10:00:00")

if ($Degradado) {
    Write-Host "`n== Resumen con clases-service detenido ==" -ForegroundColor Cyan
    if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) {
        Write-Host "[AVISO] clases-service sigue escuchando en 8082; detengalo para este caso." -ForegroundColor Yellow
    }
    $r = Expect 'resumen miembro 1 con clases caido' @(200) '/api/resumen/miembros/1' 'GET' 'member' $null
    $j = $r.Body | ConvertFrom-Json
    Check 'clases es null' @($true) ($null -eq $j.clases) $r.Body
    Check 'advertencias menciona las clases' @($true) (@($j.advertencias | Where-Object { $_ -match 'clases' }).Count -ge 1) $r.Body
    Check 'miembro y pagos siguen presentes' @($true) (($null -ne $j.miembro) -and ($null -ne $j.pagos)) $r.Body
    Write-Host ($r.Body | ConvertFrom-Json | ConvertTo-Json -Depth 6)
    Write-Host ("`nResultado: {0} OK, {1} fallos" -f $script:ok, $script:fallos) -ForegroundColor $(if ($script:fallos) { 'Red' } else { 'Green' })
    if ($script:fallos) { exit 1 }
    exit 0
}

Write-Host "`n== Seguridad en el gateway ==" -ForegroundColor Cyan
Expect 'actuator/health sin token'            @(200) '/actuator/health' 'GET' $null $null | Out-Null
Expect 'GET /api/miembros sin token'          @(401) '/api/miembros'    'GET' $null $null | Out-Null
Expect 'POST /api/miembros sin token'         @(401) '/api/miembros'    'POST' $null '{}' | Out-Null
Expect 'member POST /api/miembros (403 del gateway)' @(403) '/api/miembros' 'POST' 'member' '{"nombre":"X","email":"x@email.com"}' | Out-Null
Expect 'member POST /api/entrenadores'        @(403) '/api/entrenadores' 'POST' 'member' '{"nombre":"X","especialidad":"Y"}' | Out-Null
Expect 'trainer POST /api/equipos'            @(403) '/api/equipos'     'POST' 'trainer' '{"nombre":"X","descripcion":"Y","cantidad":1}' | Out-Null
Expect 'trainer POST /api/pagos'              @(403) '/api/pagos'       'POST' 'trainer' '{"miembroId":1,"monto":10,"concepto":"x"}' | Out-Null

Write-Host "`n== admin: POST y GET de los 4 recursos ==" -ForegroundColor Cyan
$m = Expect 'admin POST /api/miembros'      @(201) '/api/miembros' 'POST' 'admin' '{"nombre":"Test Gateway","email":"gateway@email.com"}'
Expect 'admin GET  /api/miembros'           @(200) '/api/miembros' 'GET'  'admin' $null | Out-Null
$e = Expect 'admin POST /api/entrenadores'  @(201) '/api/entrenadores' 'POST' 'admin' '{"nombre":"Entrenador Gateway","especialidad":"Pilates"}'
Expect 'admin GET  /api/entrenadores'       @(200) '/api/entrenadores' 'GET'  'admin' $null | Out-Null
$entrenadorId = ($e.Body | ConvertFrom-Json).id
$c = Expect 'admin POST /api/clases'        @(201) '/api/clases' 'POST' 'admin' (@{nombre='Clase Admin';horario=$manana;capacidadMaxima=10;entrenadorId=$entrenadorId} | ConvertTo-Json)
Expect 'admin GET  /api/clases'             @(200) '/api/clases' 'GET'  'admin' $null | Out-Null
Expect 'admin POST /api/equipos'            @(201) '/api/equipos' 'POST' 'admin' '{"nombre":"Banco Gateway","descripcion":"Prueba","cantidad":5}' | Out-Null
Expect 'admin GET  /api/equipos'            @(200) '/api/equipos' 'GET'  'admin' $null | Out-Null
Expect 'admin POST /api/pagos'              @(202) '/api/pagos' 'POST' 'admin' '{"miembroId":1,"monto":50000,"concepto":"Prueba admin"}' | Out-Null

Write-Host "`n== trainer y member ==" -ForegroundColor Cyan
$ct = Expect 'trainer POST /api/clases'     @(201) '/api/clases' 'POST' 'trainer' (@{nombre='Clase Trainer';horario=$manana;capacidadMaxima=10;entrenadorId=$entrenadorId} | ConvertTo-Json)
$claseId = ($ct.Body | ConvertFrom-Json).id
Expect 'member inscripcion en la clase'     @(201) "/api/clases/$claseId/inscripciones" 'POST' 'member' '{"miembroId":1}' | Out-Null
Expect 'member GET /api/clases'             @(200) '/api/clases' 'GET' 'member' $null | Out-Null
Expect 'member cancela su inscripcion'      @(200) "/api/clases/$claseId/inscripciones/1" 'DELETE' 'member' $null | Out-Null
Expect 'trainer inscripcion (403)'          @(403) "/api/clases/$claseId/inscripciones" 'POST' 'trainer' '{"miembroId":1}' | Out-Null

Write-Host "`n== Resumen de miembro (filtro ResumenMiembro) ==" -ForegroundColor Cyan
# Inscribir al miembro 1 en la clase 1 (si ya lo estaba de una corrida anterior, no se repite)
$clase1 = Api "$gw/api/clases/1" 'GET' (Token 'member') $null
if ($clase1.miembrosInscritos -contains 1) {
    Write-Host '[OK]    miembro 1 ya estaba inscrito en la clase 1' -ForegroundColor Green
    $script:ok++
} else {
    Expect 'member inscribe al miembro 1 en la clase 1' @(201) '/api/clases/1/inscripciones' 'POST' 'member' '{"miembroId":1}' | Out-Null
}
# Pago valido (se procesa de forma asincrona por RabbitMQ)
$pago = Expect 'member registra un pago valido' @(202) '/api/pagos' 'POST' 'member' '{"miembroId":1,"monto":120000,"concepto":"Mensualidad (test-gateway)"}'
Start-Sleep -Seconds 2

$r = Expect 'GET /api/resumen/miembros/1' @(200) '/api/resumen/miembros/1' 'GET' 'member' $null
$resumen = $r.Body | ConvertFrom-Json
Check 'resumen: trae el miembro 1' @(1) $resumen.miembro.id $r.Body
Check 'resumen: al menos una clase' @($true) ($resumen.totalClases -ge 1) $r.Body
Check 'resumen: la clase 1 incluye entrenador' @($true) (@($resumen.clases | Where-Object { $_.id -eq 1 -and $_.entrenadorNombre }).Count -eq 1) $r.Body
Check 'resumen: incluye el pago recien hecho' @($true) (@($resumen.pagos | Where-Object { $_.id -eq ($pago.Body | ConvertFrom-Json).pagoId }).Count -eq 1) $r.Body
Check 'resumen: totalPagado >= 120000' @($true) ($resumen.totalPagado -ge 120000) $r.Body
Check 'resumen: sin advertencias' @(0) @($resumen.advertencias).Count $r.Body
Write-Host ($resumen | ConvertTo-Json -Depth 6)

Expect 'resumen de miembro inexistente' @(404) '/api/resumen/miembros/9999' 'GET' 'member' $null | Out-Null
Expect 'resumen con id no numerico'     @(400) '/api/resumen/miembros/abc'  'GET' 'member' $null | Out-Null
Expect 'resumen sin token'              @(401) '/api/resumen/miembros/1'    'GET' $null $null | Out-Null

Write-Host ("`nResultado: {0} OK, {1} fallos" -f $script:ok, $script:fallos) -ForegroundColor $(if ($script:fallos) { 'Red' } else { 'Green' })
if ($script:fallos) { exit 1 }
Write-Host "Para el caso de degradacion: detenga clases-service y corra  scripts\test-gateway.ps1 -Degradado"
