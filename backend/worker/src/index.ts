/**
 * Together ❤️ — দুজনের নিরাপদ সঙ্গ
 * Cloudflare Workers Backend API
 * Zero-cost serverless architecture on Cloudflare Workers Free plan.
 */

export interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_CLIENT_EMAIL?: string;
  FIREBASE_PRIVATE_KEY?: string; // Stored securely in Cloudflare Secrets
}

interface LocationPayload {
  pairId: string;
  latitude: number;
  longitude: number;
  accuracy: number;
  speed?: number;
  batteryLevel?: number;
  isMoving?: boolean;
  timestamp: number;
}

// Distance Calculation using Haversine Formula (Meters)
function calculateHaversineDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000; // Earth radius in meters
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) * Math.cos(lat2 * (Math.PI / 180)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

// Fast JWT Payload decoder & verification
async function verifyFirebaseToken(idToken: string, projectId: string): Promise<{ uid: string; email?: string } | null> {
  try {
    const parts = idToken.split('.');
    if (parts.length !== 3) return null;

    const payloadJson = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
    const payload = JSON.parse(payloadJson);

    // Verify audience and issuer
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp < now) return null;
    if (payload.aud !== projectId && projectId) return null;
    if (payload.iss !== `https://securetoken.google.com/${projectId}` && projectId) return null;

    return { uid: payload.sub || payload.user_id, email: payload.email };
  } catch (err) {
    return null;
  }
}

// CORS Headers helper
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const projectId = env.FIREBASE_PROJECT_ID || 'together-cf667';

    // Verify Authorization Header (Bearer Firebase ID Token)
    const authHeader = request.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return new Response(JSON.stringify({ error: 'অননুমোদিত অনুরোধ (Missing Bearer Token)' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const token = authHeader.substring(7);
    const user = await verifyFirebaseToken(token, projectId);
    if (!user) {
      return new Response(JSON.stringify({ error: 'অবৈধ বা মেয়াদোত্তীর্ণ টোকেন' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Router
    try {
      if (url.pathname === '/api/location/update' && request.method === 'POST') {
        const body = (await request.json()) as LocationPayload;

        // Validation: Ignore inaccurate readings > 50m
        if (body.accuracy > 50) {
          return new Response(JSON.stringify({ status: 'ignored', reason: 'High GPS inaccuracy (>50m)' }), {
            status: 200,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          });
        }

        // Validate timestamp skew (< 60s)
        const currentMs = Date.now();
        if (Math.abs(currentMs - body.timestamp) > 60000) {
          body.timestamp = currentMs;
        }

        return new Response(
          JSON.stringify({
            status: 'success',
            uid: user.uid,
            receivedAt: currentMs,
          }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (url.pathname === '/api/geofence/check' && request.method === 'POST') {
        const { currentLat, currentLon, homeLat, homeLon, previousZone } = (await request.json()) as any;
        const distance = calculateHaversineDistance(currentLat, currentLon, homeLat, homeLon);

        let newZone = previousZone;
        let alertMessage: string | null = null;
        let eventType: string | null = null;

        if (distance <= 100) {
          newZone = 'APPROACHING_100';
          alertMessage = 'গেট খুলে রাখো, আর মাত্র ১০০ মিটার দূরে!';
          eventType = 'APPROACHING_100';
        } else if (distance <= 500) {
          if (previousZone === 'OUTSIDE_500') {
            newZone = 'INSIDE_500';
            alertMessage = 'আপনার কলিজা আসতেছে ❤️ গেট খুলে রাখো।';
            eventType = 'ENTRY_500';
          }
        } else if (distance > 525) {
          // Hysteresis buffer (525m)
          if (previousZone === 'INSIDE_500' || previousZone === 'APPROACHING_100') {
            newZone = 'OUTSIDE_500';
            alertMessage = 'আপনার কলিজা চলে গেছে ❤️ গেট লাগিয়ে রাখো।';
            eventType = 'EXIT_500';
          }
        }

        return new Response(
          JSON.stringify({
            distanceMeters: Math.round(distance),
            newZone,
            alertMessage,
            eventType,
          }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      if (url.pathname === '/api/health') {
        return new Response(
          JSON.stringify({
            status: 'online',
            service: 'Together ❤️ Cloudflare Worker',
            project: projectId,
            timestamp: new Date().toISOString(),
          }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      return new Response(JSON.stringify({ error: 'পাথ খুঁজে পাওয়া যায়নি' }), {
        status: 404,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    } catch (e: any) {
      return new Response(JSON.stringify({ error: e.message || 'সার্ভার ত্রুটি' }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
  },
};
